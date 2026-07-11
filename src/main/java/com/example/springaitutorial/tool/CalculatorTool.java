package com.example.springaitutorial.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 一个只允许做加法的简单工具，用于演示工具参数。
 */
public class CalculatorTool {

    @Tool(description = "计算两个整数的和，只用于简单加法，不执行任意代码")
    public String add(
            @ToolParam(description = "第一个整数", required = true) int left,
            @ToolParam(description = "第二个整数", required = true) int right) {
        return String.valueOf(Math.addExact(left, right));
    }
}
