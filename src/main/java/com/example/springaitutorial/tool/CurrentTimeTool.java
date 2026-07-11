package com.example.springaitutorial.tool;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 提供给大模型调用的 Java 工具。
 */
public class CurrentTimeTool {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Tool(description = "查询指定时区的当前日期和时间")
    public String getCurrentTime(
            @ToolParam(description = "Java 时区 ID，例如 Asia/Shanghai 或 UTC", required = true) String zoneId) {
        return LocalDateTime.now(ZoneId.of(zoneId)).format(FORMATTER);
    }
}
