package com.example.springaitutorial.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.tokenizer.TokenCountEstimator;

/**
 * 异步摘要记忆：先保存当前消息，后台再生成摘要，不阻塞当前响应。
 */
public class AsyncSummaryChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(AsyncSummaryChatMemory.class);
    private static final String SUMMARY_PREFIX = "[历史摘要]\n";

    private final ChatMemory delegate;
    private final ChatClient summarizer;
    private final TokenCountEstimator tokenCountEstimator;
    private final int summaryThreshold;
    private final int recentMessageCount;
    private final Set<String> summarizingConversations = ConcurrentHashMap.newKeySet();

    public AsyncSummaryChatMemory(ChatMemory delegate,
                                  ChatClient summarizer,
                                  TokenCountEstimator tokenCountEstimator,
                                  int summaryThreshold,
                                  int recentMessageCount) {
        this.delegate = delegate;
        this.summarizer = summarizer;
        this.tokenCountEstimator = tokenCountEstimator;
        this.summaryThreshold = summaryThreshold;
        this.recentMessageCount = recentMessageCount;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        // 先保存消息，让当前请求尽快完成；这里不调用总结模型。
        delegate.add(conversationId, messages);

        boolean assistantMessageAdded = messages.stream()
                .anyMatch(message -> message.getMessageType() == MessageType.ASSISTANT);
        if (assistantMessageAdded && exceedsThreshold(conversationId)
                && summarizingConversations.add(conversationId)) {
            CompletableFuture.runAsync(() -> summarize(conversationId))
                    .whenComplete((result, error) -> summarizingConversations.remove(conversationId));
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        return delegate.get(conversationId);
    }

    @Override
    public void clear(String conversationId) {
        delegate.clear(conversationId);
    }

    private boolean exceedsThreshold(String conversationId) {
        return delegate.get(conversationId).stream()
                .filter(message -> message.getMessageType() != MessageType.SYSTEM)
                .mapToInt(message -> tokenCountEstimator.estimate(message.getText()))
                .sum() > summaryThreshold;
    }

    private void summarize(String conversationId) {
        try {
            List<Message> ordinaryMessages = delegate.get(conversationId).stream()
                    .filter(message -> message.getMessageType() != MessageType.SYSTEM)
                    .toList();

            String conversationText = ordinaryMessages.stream()
                    .map(message -> message.getMessageType() + ": " + message.getText())
                    .collect(Collectors.joining("\n"));

            String summary = summarizer.prompt()
                    .system("你负责压缩聊天记录。请保留用户身份、目标、已确认事实和未完成事项，使用简洁中文输出摘要。")
                    .user("请总结下面的聊天记录：\n\n" + conversationText)
                    .call()
                    .content();

            List<Message> recentMessages = ordinaryMessages.subList(
                    Math.max(0, ordinaryMessages.size() - recentMessageCount),
                    ordinaryMessages.size());
            List<Message> compactedMessages = new ArrayList<>();
            compactedMessages.add(new SystemMessage(SUMMARY_PREFIX + summary));
            compactedMessages.addAll(recentMessages);

            delegate.clear(conversationId);
            delegate.add(conversationId, compactedMessages);
            log.info("异步摘要完成: conversationId={}, recentMessages={}",
                    conversationId, recentMessages.size());
        }
        catch (Exception ex) {
            // 摘要失败不能影响已经返回给用户的当前回答。
            log.warn("异步摘要失败: conversationId={}", conversationId, ex);
        }
    }
}
