package com.example.demo.repository;

import com.example.demo.entity.ConversationSession;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ConversationSessionRepository  extends ReactiveCrudRepository<ConversationSession,Long> {
    Flux<ConversationSession> findAllByOrderByUpdatedAtDesc();
}
