package com.example.demo.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("conversation_sessions")
public record ConversationSession(
    @Id
    Long id,

    @Column("title")
    String title,

    @Column("created_at")
    LocalDateTime createdAt,

    @Column("updated_at")
    LocalDateTime updatedAt
){

    public static ConversationSession create(String title){
        LocalDateTime now=LocalDateTime.now();
        return new ConversationSession(
                null,
                title,
                now,
                now
        );
    }

    public ConversationSession withUpdatedTime(){
        return new ConversationSession(
                this.id,
                this.title,
                this.createdAt,
               LocalDateTime.now()
        );
    }
}
