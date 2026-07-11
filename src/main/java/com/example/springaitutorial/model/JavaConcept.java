package com.example.springaitutorial.model;

import java.util.List;

/**
 * AI 返回结果的目标结构。
 * Spring AI 会根据这个类型，把模型返回的 JSON 转换成 Java 对象。
 */
public record JavaConcept(
        String name,
        String definition,
        String analogy,
        List<String> examples
) {
}
