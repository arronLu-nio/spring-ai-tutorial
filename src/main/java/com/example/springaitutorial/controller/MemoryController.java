package com.example.springaitutorial.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import com.example.springaitutorial.session.ConversationService;
import com.example.springaitutorial.tool.CurrentTimeTool;
import com.example.springaitutorial.tool.CalculatorTool;
import com.example.springaitutorial.tool.JavaInfoTool;
import com.example.springaitutorial.advisor.ToolMessageSavingAdvisor;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.model.tool.ToolCallingManager;

@RestController
public class MemoryController {

    private final ChatClient chatClient;
    private final MessageChatMemoryAdvisor memoryAdvisor;
    private final ConversationService conversationService;
    private final ToolMessageSavingAdvisor toolMessageSavingAdvisor;

    public MemoryController(ChatClient.Builder chatClientBuilder,
                            ChatMemory chatMemory,
                            ConversationService conversationService) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
        // Spring AI 默认提供内存版 ChatMemory，适合学习和本地 Demo。
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        this.conversationService = conversationService;
        this.toolMessageSavingAdvisor = new ToolMessageSavingAdvisor(
                chatMemory,
                ToolCallingManager.builder().build());
    }

    @GetMapping(value = "/ai/memory/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestParam String conversationId,
                             @RequestParam String message) {
        conversationService.touch(conversationId);
        return chatClient.prompt()
                // system message：定义本次请求的角色和回答规则，每次请求都会重新加入
                .system("你是一名耐心的 Java 和 Spring AI 老师，请使用简单的中文回答。")
                // tools：让模型在多轮对话中也可以调用当前时间工具。
                // 注册多个工具，模型会根据用户问题选择合适的工具。
                .tools(new CurrentTimeTool(), new JavaInfoTool(), new CalculatorTool())
                .advisors(advisorSpec -> advisorSpec
                        // 把记忆 Advisor 加入本次请求
                        .advisors(memoryAdvisor)
                        // 保存 Assistant tool call 和 ToolResponseMessage
                        .advisors(toolMessageSavingAdvisor)
                        // conversationId 决定读取和保存哪一段对话
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                // 使用自定义 ToolCallingAdvisor，关闭框架自动注册的默认版本。
                .advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
                .user(message)
                .stream()
                .content();
    }
}
