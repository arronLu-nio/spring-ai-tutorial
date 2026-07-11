package com.example.springaitutorial.tool;

import org.springframework.ai.tool.annotation.Tool;

/**
 * 查询当前应用运行时的 Java 信息。
 */
public class JavaInfoTool {

    @Tool(description = "查询当前应用使用的 Java 版本和运行时名称")
    public String getJavaRuntimeInfo() {
        return "Java 版本：" + System.getProperty("java.version")
                + "，运行时：" + System.getProperty("java.runtime.name");
    }
}
