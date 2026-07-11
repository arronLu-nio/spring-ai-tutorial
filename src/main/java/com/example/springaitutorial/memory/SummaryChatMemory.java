package com.example.springaitutorial.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.tokenizer.TokenCountEstimator;

/**
 * 摘要记忆：历史超过 Token 阈值后，压缩成摘要并保留最近几条原始消息。
 */
public class SummaryChatMemory implements ChatMemory {

    private static final String SUMMARY_PREFIX = "[历史摘要]\n";

    private final ChatMemory delegate;
    private final ChatClient summarizer;
    private final TokenCountEstimator tokenCountEstimator;
    private final int summaryThreshold;
    private final int recentMessageCount;

    public SummaryChatMemory(ChatMemory delegate,
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
    public void add(String conversationId, List<Message> newMessages) {
        List<Message> allMessages = new ArrayList<>(delegate.get(conversationId));
        allMessages.addAll(newMessages);

        List<Message> ordinaryMessages = allMessages.stream()
                .filter(message -> message.getMessageType() != MessageType.SYSTEM)
                .toList();

        int totalTokens = ordinaryMessages.stream()
                .mapToInt(message -> tokenCountEstimator.estimate(message.getText()))
                .sum();

        if (totalTokens <= summaryThreshold) {
            delegate.add(conversationId, newMessages);
            return;
        }

        String conversationText = ordinaryMessages.stream()
                .map(message -> message.getMessageType() + ": " + message.getText())
                .collect(Collectors.joining("\n"));

        String summary = summarizer.prompt()
                .system("你负责压缩聊天记录。请保留用户身份、目标、已确认事实和未完成事项，使用简洁中文输出摘要。")
                .user("请总结下面的聊天记录：\n\n" + conversationText)
                // 摘要本身是一次同步模型调用，生产环境应增加超时和失败兜底。
                .call()
                .content();

        List<Message> recentMessages = ordinaryMessages.subList(
                Math.max(0, ordinaryMessages.size() - recentMessageCount),
                ordinaryMessages.size());

        List<Message> compactedMessages = new ArrayList<>();
        compactedMessages.add(new SystemMessage(SUMMARY_PREFIX + summary));
        compactedMessages.addAll(recentMessages);

        // 用“摘要 + 最近消息”替换原来的完整历史。
        delegate.clear(conversationId);
        delegate.add(conversationId, compactedMessages);
    }

    @Override
    public List<Message> get(String conversationId) {
        return delegate.get(conversationId);
    }

    @Override
    public void clear(String conversationId) {
        delegate.clear(conversationId);
    }
}
