package com.example.springaitutorial.controller;

import com.example.springaitutorial.model.JavaConcept;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class StructuredOutputController {

    private static final Logger log = LoggerFactory.getLogger(StructuredOutputController.class);
    private final ChatClient chatClient;

    public StructuredOutputController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                // 开发阶段打印结构化输出请求和响应
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    @GetMapping("/ai/structured-output")
    public JavaConcept explain(@RequestParam String topic) {
        return chatClient.prompt()
                .system("请用中文解释 Java 或 Spring 概念，内容要适合初学者。")
                .user("请解释这个概念：" + topic)
                .call()                         // 同步等待完整结果
                // entity 的底层过程：
                // 1. 根据 JavaConcept.class 推断需要返回的字段结构
                // 2. 将结构要求加入 Prompt，要求模型返回 JSON
                // 3. 获取模型返回的 JSON 文本
                // 4. 使用 Spring AI 的结构化输出转换器反序列化 JSON
                // 5. 最终得到一个可以直接在 Java 中使用的 JavaConcept 对象
                .entity(JavaConcept.class);
    }

    @GetMapping("/ai/structured-output/safe")
    public ResponseEntity<?> explainSafely(@RequestParam String topic) {
        try {
            JavaConcept result = chatClient.prompt()
                    .system("请用中文解释 Java 或 Spring 概念，内容要适合初学者。")
                    .user("请解释这个概念：" + topic)
                    .call()
                    .entity(JavaConcept.class);

            return ResponseEntity.ok(result);
        } catch (Exception exception) {
            // 模型返回非法 JSON、字段类型不匹配等问题，都会在转换阶段抛出异常。
            log.error("结构化输出解析失败，topic={}", topic, exception);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "STRUCTURED_OUTPUT_PARSE_FAILED",
                    "message", "AI 返回的内容无法转换成 JavaConcept，请稍后重试"
            ));
        }
    }
}
