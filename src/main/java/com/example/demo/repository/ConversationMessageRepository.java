package com.example.demo.repository;


import com.example.demo.entity.ConversationMessage;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 消息数据访问接口
 */
public interface ConversationMessageRepository
        extends ReactiveCrudRepository<ConversationMessage, Long> {

    /**
     * 按时间正序查询会话的所有消息
     */
    Flux<ConversationMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    /**
     * 删除会话的所有消息
     */
    Mono<Void> deleteBySessionId(Long sessionId);
}
