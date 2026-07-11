package com.example.springaitutorial.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * AI 返回结果的目标结构。
 * Spring AI 会根据这个类型，把模型返回的 JSON 转换成 Java 对象。
 */
public record JavaConcept(
        @NotBlank(message = "概念名称不能为空")
        String name,
        @NotBlank(message = "概念定义不能为空")
        String definition,
        @NotBlank(message = "概念类比不能为空")
        String analogy,
        @NotEmpty(message = "至少需要一个代码示例")
        @Size(max = 5, message = "代码示例不能超过 5 个")
        List<String> examples
) {
}
