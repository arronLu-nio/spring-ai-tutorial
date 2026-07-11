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

    @GetMapping("/ai/prompt/template")
    public String explainTopic(@RequestParam String topic) {
        return chatClient.prompt()
                // user 文本中的 {topic} 是模板变量
                .user(user -> user
                        .text("请用简单的中文解释 {topic}，并给出一个 Java 示例。")
                        // param：在运行时把 topic 的值填入模板
                        .param("topic", topic))
                .call()                  // 同步等待完整回答
                .content();              // 提取文本内容
    }
}
