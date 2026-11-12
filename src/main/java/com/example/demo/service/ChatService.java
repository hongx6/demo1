package com.example.demo.service;

import com.example.demo.config.ChatProperties;
import com.example.demo.entity.ConversationMessage;
import com.example.demo.entity.ConversationSession;
import com.example.demo.repository.ConversationMessageRepository;
import com.example.demo.repository.ConversationSessionRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ChatService {
    private final ChatClient chatClient;
    private final ConversationSessionRepository sessionRepository;
    private final ConversationMessageRepository messageRepository;
    private final ChatProperties chatProperties;
    private final RagService ragService;

    public ChatService(
            ChatModel chatModel,
            ConversationSessionRepository sessionRepository,
            ConversationMessageRepository messageRepository,
            ChatProperties chatProperties,
            RagService ragService
    ) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.chatProperties = chatProperties;
        this.ragService = ragService;
    }

    // ========== 普通对话 ==========
    public Mono<String> chat(Long sessionId, String userMessage) {
        if (sessionId == null) {
            return Mono.error(new IllegalArgumentException("sessionID不能为空,请先创建对话"));
        }
        if (userMessage == null || userMessage.isBlank()) {
            return Mono.error(new IllegalArgumentException("用户消息不能为空"));
        }

        return validateSessionExists(sessionId)
                .flatMap(sid -> buildConversationHistory(sid)
                        .flatMap(history -> retrieveDocuments(userMessage)
                                .flatMap(documents -> {
                                    history.set(0, new SystemMessage(
                                            ragService.buildSystemPrompt(
                                                    chatProperties.getSystemPrompt(), documents)));
                                    // 把用户消息加入上下文（仅用于发给AI，不立即落库）
                                    history.add(new UserMessage(userMessage));
                                    Prompt prompt = new Prompt(new ArrayList<>(history));

                                    return Mono.fromCallable(() ->
                                                    chatClient.prompt(prompt)
                                                            .call()
                                                            .content()
                                            )
                                            .subscribeOn(Schedulers.boundedElastic())
                                            .flatMap(response -> {
                                                if (response != null && !response.isEmpty()) {
                                                    // AI 成功回复后才落库
                                                    return saveMessage(sid, MessageType.USER.getValue(), userMessage)
                                                            .then(saveMessage(sid, MessageType.ASSISTANT.getValue(), response))
                                                            .thenReturn(response);
                                                }
                                                return Mono.justOrEmpty(response);
                                            })
                                            .onErrorResume(Exception.class, e ->
                                                    Mono.just("【请求失败】" + extractErrorMessage(e))
                                            );
                                })));
    }

    // ========== 流式对话模式 ==========
    /**
     * 流式聊天（SSE）
     *
     * 执行流程：
     * 1. 验证 sessionId 有效性
     * 2. 构建对话历史
     * 3. 添加当前用户消息
     * 4. 调用 AI 流式接口
     * 5. 实时返回流式数据
     * 6. 流完成后异步保存消息
     *
     * @param sessionId 会话ID，必须提供
     * @param userMessage 用户消息
     * @return 流式AI回复（Flux<String>）
     */
    public Flux<String> chatStream(Long sessionId, String userMessage) {
        if (sessionId == null) {
            return Flux.error(new IllegalArgumentException("sessionId 不能为空，请先创建会话"));
        }
        if (userMessage == null || userMessage.isBlank()) {
            return Flux.error(new IllegalArgumentException("用户消息不能为空"));
        }

        return validateSessionExists(sessionId)
                .flatMapMany(sid -> buildConversationHistory(sid)
                        .flatMapMany(history -> retrieveDocuments(userMessage)
                                .flatMapMany(documents -> {
                                    history.set(0, new SystemMessage(
                                            ragService.buildSystemPrompt(
                                                    chatProperties.getSystemPrompt(), documents)));
                                    // 添加用户消息到历史
                                    history.add(new UserMessage(userMessage));

                                    Prompt prompt = new Prompt(new ArrayList<>(history));
                                    StringBuilder fullResponse = new StringBuilder();

                                    return chatClient.prompt(prompt)
                                            .stream()           // 启用流式输出
                                            .content()          // 获取内容流
                                            .doOnNext(fullResponse::append)  // 累积完整回复
                                            .doOnComplete(() -> {
                                                // 流完成后异步保存消息
                                                if (!fullResponse.isEmpty()) {
                                                    String finalResponse = fullResponse.toString();
                                                    saveMessage(sid, MessageType.USER.getValue(), userMessage)
                                                            .then(saveMessage(sid, MessageType.ASSISTANT.getValue(), finalResponse))
                                                            .subscribeOn(Schedulers.boundedElastic())
                                                            .subscribe(
                                                                    v -> {},
                                                                    err -> logError("保存流式消息失败", err)
                                                            );
                                                }
                                            })
                                            .onErrorResume(Exception.class, e ->
                                                    Flux.just("【请求失败】" + extractErrorMessage(e))
                                            );
                                })));
    }

    // ========== 构建对话历史（带上下文长度控制） ==========
    /**
     * 构建对话历史（带上下文长度控制）
     *
     * 策略：
     * 1. 始终包含系统消息（从配置读取）
     * 2. 从数据库获取该会话的所有历史消息
     * 3. 如果消息数量超过限制，保留最近的 maxContextMessages 条
     *
     * @param sessionId 会话ID
     * @return 构建好的消息列表（包含系统消息）
     */
    private Mono<List<Message>> buildConversationHistory(Long sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                .collectList()
                .map(messages -> {
                    List<Message> history = new ArrayList<>();

                    // 1. 添加系统消息（从配置读取）
                    history.add(new SystemMessage(chatProperties.getSystemPrompt()));

                    // 2. 处理历史消息（上下文截断）
                    List<ConversationMessage> contextMessages = messages;
                    int maxContextMessages = chatProperties.getMaxContextMessages();

                    // 如果消息过多，只保留最近的 maxContextMessages 条
                    if (messages.size() > maxContextMessages) {
                        contextMessages = messages.subList(
                                messages.size() - maxContextMessages,
                                messages.size()
                        );
                    }

                    // 3. 转换为 Spring AI Message 对象
                    for (ConversationMessage msg : contextMessages) {
                        if (MessageType.USER.getValue().equals(msg.role())) {
                            history.add(new UserMessage(msg.content()));
                        } else if (MessageType.ASSISTANT.getValue().equals(msg.role())) {
                            history.add(new AssistantMessage(msg.content()));
                        }
                        // 系统消息已在开头添加，数据库中的系统消息忽略
                    }

                    return history;
                });
    }

    // ========== 创建新会话 ==========
    /**
     * 创建新会话
     *
     * @param title 会话标题（可选，默认为"新会话"）
     * @return 新会话ID
     */
    public Mono<Long> createNewSession(String title) {
        String sessionTitle = (title == null || title.isBlank()) ? "新会话" : title;
        return sessionRepository.save(ConversationSession.create(sessionTitle))
                .map(ConversationSession::id);
    }


    /**
     * 根据ID获取单个会话
     */
    public Mono<ConversationSession> getSession(Long sessionId) {
        if (sessionId == null) {
            return Mono.error(new IllegalArgumentException("sessionId 不能为空"));
        }
        return sessionRepository.findById(sessionId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("会话不存在: " + sessionId)));
    }

    // ========== 获取所有会话列表 ==========
    /**
     * 获取所有会话列表（按更新时间倒序）
     *
     * @return 会话列表
     */
    public Flux<ConversationSession> getAllSessions() {
        return sessionRepository.findAllByOrderByUpdatedAtDesc();
    }

    // ========== 获取指定会话的所有消息 ==========
    /**
     * 获取指定会话的所有消息
     *
     * @param sessionId 会话ID
     * @return 消息列表
     */
    public Flux<ConversationMessage> getSessionMessages(Long sessionId) {
        if (sessionId == null) {
            return Flux.error(new IllegalArgumentException("sessionId 不能为空"));
        }
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    // ========== 删除会话及其所有消息 ==========
    /**
     * 删除会话及其所有消息
     *
     * @param sessionId 要删除的会话ID
     * @return 操作完成信号
     */
    public Mono<Void> deleteSession(Long sessionId) {
        if (sessionId == null) {
            return Mono.error(new IllegalArgumentException("sessionId 不能为空"));
        }

        // 先删除消息，再删除会话（利用外键级联删除也可）
        return messageRepository.deleteBySessionId(sessionId)
                .then(sessionRepository.deleteById(sessionId));
    }

    // ========== 验证会话是否存在 ==========
    private Mono<Long> validateSessionExists(Long sessionId) {
        return sessionRepository.existsById(sessionId)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.just(sessionId);
                    }
                    return Mono.error(new NoSuchElementException("会话不存在: " + sessionId));
                });
    }

    // ========== 保存消息到数据库 ==========
    private Mono<Void> saveMessage(Long sessionId, String role, String content) {
        return messageRepository.save(ConversationMessage.of(sessionId, role, content))
                .then();
    }

    private Mono<List<Document>> retrieveDocuments(String query) {
        return Mono.fromCallable(() -> ragService.search(query))
                .subscribeOn(Schedulers.boundedElastic());
    }

    // ========== 提取错误消息（之前缺失的方法） ==========
    private String extractErrorMessage(Throwable e) {
        if (e == null) {
            return "未知错误";
        }
        String message = e.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        return e.getClass().getSimpleName();
    }

    // ========== 错误日志记录（之前缺失的方法） ==========
    private void logError(String message, Throwable error) {
        // 实际项目中替换为 Logger
        System.err.println(message + ": " + error.getMessage());
        error.printStackTrace();
    }
}
