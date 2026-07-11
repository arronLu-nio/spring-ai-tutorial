package com.example.springaitutorial;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PromptController {

    private final ChatClient chatClient;

    public PromptController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @GetMapping("/ai/prompt")
    public String explain(@RequestParam String question) {
        return chatClient.prompt()
                // system：规定 AI 的身份、语气和行为规则
                .system("你是一名耐心的 Java 和 Spring AI 老师，请使用简单的中文回答小白问题")
                // user：传入用户这一次真正想问的问题
                .user(question)
                // call：同步调用模型，等待完整回答
                .call()
                // content：只提取回答中的文本内容
                .content();
    }
}

