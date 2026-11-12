package com.example.demo.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("conversation_messages")
public record ConversationMessage(
        @Id
        Long id,

        @Column("session_id")
        Long sessionId,

        @Column("role")
        String role,        // "user" 或 "assistant"

        @Column("content")
        String content,

        @Column("created_at")
        LocalDateTime createdAt
) {
    /**
     * 工厂方法：创建新消息
     */
    public static ConversationMessage of(Long sessionId, String role, String content) {
        return new ConversationMessage(
                null,
                sessionId,
                role,
                content,
                LocalDateTime.now()
        );
    }
}
