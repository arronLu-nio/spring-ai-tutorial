package com.example.springaitutorial.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        List<Message> safeMessages = messages.stream()
                .map(this::sanitizeMessageMetadata)
                .toList();

        logMessages("saveAll-before", conversationId, safeMessages);
        delegate.saveAll(conversationId, safeMessages);
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

    private Message sanitizeMessageMetadata(Message message) {
        Map<String, Object> safeMetadata = new LinkedHashMap<>();
        message.getMetadata().forEach((key, value) -> {
            // Redis Search 的 TEXT 字段只接受字符串；复杂值转成可读字符串保存。
            if (value == null || value instanceof String) {
                safeMetadata.put(key, value);
            } else {
                safeMetadata.put(key, String.valueOf(value));
            }
        });

        if (message instanceof UserMessage) {
            return UserMessage.builder()
                    .text(message.getText())
                    .metadata(safeMetadata)
                    .build();
        }

        if (message instanceof AssistantMessage) {
            return AssistantMessage.builder()
                    .content(message.getText())
                    .properties(safeMetadata)
                    .build();
        }

        return message;
    }
}
