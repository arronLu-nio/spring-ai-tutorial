package com.example.springaitutorial.memory;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

/**
 * 自定义保留策略：保留第一条用户消息，再保留最近 N 条普通消息。
 */
public class FirstUserPlusRecentChatMemory implements ChatMemory {

    private final ChatMemory delegate;
    private final int recentMessageCount;

    public FirstUserPlusRecentChatMemory(ChatMemory delegate, int recentMessageCount) {
        this.delegate = delegate;
        this.recentMessageCount = recentMessageCount;
    }

    @Override
    public void add(String conversationId, List<Message> newMessages) {
        List<Message> allMessages = new ArrayList<>(delegate.get(conversationId));
        allMessages.addAll(newMessages);

        List<Message> selectedMessages = selectMessages(allMessages);

        // ChatMemory 没有 replace 方法，这里用“清空后写入快照”实现替换。
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

    private List<Message> selectMessages(List<Message> messages) {
        List<Message> ordinaryMessages = messages.stream()
                .filter(message -> message.getMessageType() != MessageType.SYSTEM)
                .toList();

        if (ordinaryMessages.isEmpty()) {
            return List.of();
        }

        Message firstUserMessage = ordinaryMessages.stream()
                .filter(message -> message.getMessageType() == MessageType.USER)
                .findFirst()
                .orElse(null);

        int recentStart = Math.max(0, ordinaryMessages.size() - recentMessageCount);
        List<Message> recentMessages = ordinaryMessages.subList(recentStart, ordinaryMessages.size());

        List<Message> selected = new ArrayList<>();
        if (firstUserMessage != null && !recentMessages.contains(firstUserMessage)) {
            selected.add(firstUserMessage);
        }
        selected.addAll(recentMessages);
        return selected;
    }
}
