package com.example.springaitutorial.controller;

import com.example.springaitutorial.model.JavaConcept;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StructuredOutputController {

    private final ChatClient chatClient;

    public StructuredOutputController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @GetMapping("/ai/structured-output")
    public JavaConcept explain(@RequestParam String topic) {
        return chatClient.prompt()
                .system("请用中文解释 Java 或 Spring 概念，内容要适合初学者。")
                .user("请解释这个概念：" + topic)
                .call()                         // 同步等待完整结果
                .entity(JavaConcept.class);     // 把 AI JSON 转换成 Java 对象
    }
}
