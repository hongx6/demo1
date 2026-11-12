package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.chat")
public class ChatProperties {

    /**
     * 系统提示词 - 定义AI助手的行为准则
     */
    private String systemPrompt = "你是一个友好、专业的AI助手，请用简洁清晰的语言回答用户的问题。";

    /**
     * 最大保留的对话轮数（一对 = user + assistant）
     * 默认20对 = 40条消息，加上系统消息共41条
     */
    private int maxContextPairs = 20;

    // Getters and Setters
    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public int getMaxContextPairs() {
        return maxContextPairs;
    }

    public void setMaxContextPairs(int maxContextPairs) {
        this.maxContextPairs = maxContextPairs;
    }

    /**
     * 获取最大保留的消息条数（不含系统消息）
     */
    public int getMaxContextMessages() {
        return maxContextPairs * 2;
    }
}