package com.example.springaitutorial.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 开发阶段使用的 ChatMemoryRepository 包装器，用于观察 Redis 前后的消息列表。
 */
public class LoggingChatMemoryRepository implements ChatMemoryRepository {

    private static final Logger log = LoggerFactory.getLogger(LoggingChatMemoryRepository.class);

    private final ChatMemoryRepository delegate;

    public LoggingChatMemoryRepository(ChatMemoryRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<String> findConversationIds() {
        return delegate.findConversationIds();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        List<Message> messages = delegate.findByConversationId(conversationId);
        logMessages("findByConversationId", conversationId, messages);
        return messages;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        logMessages("saveAll-before", conversationId, messages);
        delegate.saveAll(conversationId, messages);
        logMessages("saveAll-after", conversationId,
                delegate.findByConversationId(conversationId));
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        log.info("deleteByConversationId: conversationId={}", conversationId);
        delegate.deleteByConversationId(conversationId);
    }

    private void logMessages(String operation, String conversationId, List<Message> messages) {
        log.info("{}: conversationId={}, count={}, messages={}",
                operation,
                conversationId,
                messages.size(),
                messages.stream()
                        .map(message -> message.getMessageType() + "[" + message.getText() + "]")
                        .toList());
    }
}

