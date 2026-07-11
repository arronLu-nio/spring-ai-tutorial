package com.example.springaitutorial;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

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

    @GetMapping(path = "/ai/prompt/template",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> explainTopic(@RequestParam String topic) {
        return chatClient.prompt()
                // user 文本中的 {topic} 是模板变量
                .user(user -> user
                        .text("请用简单的中文解释 {topic}，并给出一个 Java 示例。")
                        // param：在运行时把 topic 的值填入模板
                        .param("topic", topic))
                .stream()                // 流式调用：生成一段就返回一段
                .content();              // 得到 Flux<String> 文本片段
    }

    @GetMapping(path = "/ai/prompt/few-shot", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> fewShot(@RequestParam String question) {
        return chatClient.prompt()
                .system("""
                        你是一个 Java 概念解释助手。
                        请严格参考下面的示例，用“定义 + 类比 + 示例”三个部分回答。

                        示例问题：什么是 String？
                        示例回答：
                        定义：String 是 Java 中表示字符串的类型。
                        类比：它像一个可以存放文字的盒子。
                        示例：String name = \"Spring AI\";
                        """)
                // user：传入新的问题，让模型模仿上面的回答格式
                .user(question)
                .stream()                // 流式返回回答片段
                .content();              // 得到 Flux<String> 文本片段
    }
}
