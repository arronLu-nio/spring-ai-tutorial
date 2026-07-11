package com.example.springaitutorial.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @GetMapping("/ai/chat")
    public String chat(@RequestParam String message) {
        return chatClient.prompt()       // 创建一次模型请求
                .user(message)           // 设置用户消息
                .call()                  // 同步调用：等待模型生成完成
                .content();              // 提取完整的文本内容
    }

    @GetMapping(value = "/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam String message) {
        return chatClient.prompt()       // 创建一次模型请求
                .user(message)           // 设置用户消息
                .stream()                // 流式调用：模型生成一段就返回一段
                .content();              // 得到 Flux<String>，逐段输出文本
    }
}
