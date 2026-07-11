package com.example.springaitutorial.controller;

import com.example.springaitutorial.tool.CurrentTimeTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演示大模型自主决定是否调用 Java 工具。
 */
@RestController
public class ToolCallingController {

    private final ChatClient chatClient;

    public ToolCallingController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @GetMapping("/ai/tool/time")
    public String currentTime(@RequestParam String question) {
        return chatClient.prompt()
                .system("你是一个 Java 助手。如果用户询问当前时间，必须调用工具获取准确时间。")
                // tools：把带有 @Tool 方法的 Java 对象注册给本次请求。
                .tools(new CurrentTimeTool())
                .user(question)
                // call：等待“判断调用工具 → 获取工具结果 → 生成回答”的完整流程。
                .call()
                .content();
    }
}
