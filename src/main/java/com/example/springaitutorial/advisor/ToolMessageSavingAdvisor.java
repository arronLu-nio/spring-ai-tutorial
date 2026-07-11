package com.example.springaitutorial.advisor;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import reactor.core.publisher.Flux;

/**
 * 在 ToolCallingAdvisor 执行工具后，把 Assistant 工具调用消息和 ToolResponseMessage
 * 一起写入 ChatMemory。
 */
public class ToolMessageSavingAdvisor extends ToolCallingAdvisor {

    private final ChatMemory chatMemory;

    public ToolMessageSavingAdvisor(ChatMemory chatMemory, ToolCallingManager toolCallingManager) {
        super(toolCallingManager,
                DEFAULT_TOOL_EXECUTION_ELIGIBILITY_CHECKER,
                DEFAULT_ORDER,
                true);
        this.chatMemory = chatMemory;
    }

    @Override
    protected List<Message> doGetNextInstructionsForToolCall(ChatClientRequest request,
                                                               ChatClientResponse response,
                                                               ToolExecutionResult result) {
        saveToolMessages(request.context(), result);
        return super.doGetNextInstructionsForToolCall(request, response, result);
    }

    @Override
    protected List<Message> doGetNextInstructionsForToolCallStream(ChatClientRequest request,
                                                                    ChatClientResponse response,
                                                                    ToolExecutionResult result) {
        saveToolMessages(request.context(), result);
        return super.doGetNextInstructionsForToolCallStream(request, response, result);
    }

    private void saveToolMessages(Map<String, Object> context, ToolExecutionResult result) {
        String conversationId = (String) context.get(ChatMemory.CONVERSATION_ID);
        if (conversationId == null) {
            return;
        }

        List<Message> history = result.conversationHistory();
        if (history.size() < 2) {
            return;
        }

        // ToolExecutionResult 的最后两条消息就是 Assistant tool call + ToolResponseMessage。
        chatMemory.add(conversationId, history.subList(history.size() - 2, history.size()));
    }
}
