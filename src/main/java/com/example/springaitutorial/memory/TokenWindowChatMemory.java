package com.example.springaitutorial.memory;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.tokenizer.TokenCountEstimator;

/**
 * 按 Token 预算保留最近消息的 ChatMemory。
 */
public class TokenWindowChatMemory implements ChatMemory {

    private final ChatMemory delegate;
    private final TokenCountEstimator tokenCountEstimator;
    private final int maxTokens;

    public TokenWindowChatMemory(ChatMemory delegate,
                                 TokenCountEstimator tokenCountEstimator,
                                 int maxTokens) {
        this.delegate = delegate;
        this.tokenCountEstimator = tokenCountEstimator;
        this.maxTokens = maxTokens;
    }

    @Override
    public void add(String conversationId, List<Message> newMessages) {
        List<Message> allMessages = new ArrayList<>(delegate.get(conversationId));
        allMessages.addAll(newMessages);

        List<Message> selectedMessages = selectRecentMessages(allMessages);
        delegate.clear(conversationId);
        if (!selectedMessages.isEmpty()) {
            delegate.add(conversationId, selectedMessages);
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

    private List<Message> selectRecentMessages(List<Message> messages) {
        List<Message> selected = new ArrayList<>();
        int usedTokens = 0;

        // 从最新消息向前计算，直到达到 Token 上限。
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message.getMessageType() == MessageType.SYSTEM) {
                continue;
            }

            int messageTokens = tokenCountEstimator.estimate(message.getText());
            if (usedTokens + messageTokens > maxTokens && !selected.isEmpty()) {
                break;
            }
            selected.add(0, message);
            usedTokens += messageTokens;
        }
        return selected;
    }
}
