package com.example.springaitutorial.memory;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;

/**
 * 自定义 ChatMemory：系统消息固定保留，普通消息交给底层窗口策略裁剪。
 */
public class SystemAwareChatMemory implements ChatMemory {

    private final ChatMemory delegate;
    private final SystemMessage systemMessage;

    public SystemAwareChatMemory(ChatMemory delegate, String systemText) {
        this.delegate = delegate;
        this.systemMessage = new SystemMessage(systemText);
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        // SystemMessage 是固定规则，不重复写入 Redis；只保存 User/Assistant 消息。
        List<Message> ordinaryMessages = messages.stream()
                .filter(message -> message.getMessageType() != MessageType.SYSTEM)
                .toList();
        if (!ordinaryMessages.isEmpty()) {
            delegate.add(conversationId, ordinaryMessages);
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        List<Message> messages = new ArrayList<>();
        // 每次读取时把系统消息放在历史消息最前面，因此它始终不会被窗口淘汰。
        messages.add(systemMessage);
        messages.addAll(delegate.get(conversationId));
        return messages;
    }

    @Override
    public void clear(String conversationId) {
        delegate.clear(conversationId);
    }
}
