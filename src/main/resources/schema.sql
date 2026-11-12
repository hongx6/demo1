-- schema.sql
-- 创建会话表
CREATE TABLE IF NOT EXISTS conversation_sessions (
                                                     id BIGSERIAL PRIMARY KEY,
                                                     title VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

-- 创建消息表
CREATE TABLE IF NOT EXISTS conversation_messages (
                                                     id BIGSERIAL PRIMARY KEY,
                                                     session_id BIGINT NOT NULL REFERENCES conversation_sessions(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,  -- 'user' 或 'assistant'
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

-- 创建索引优化查询
CREATE INDEX IF NOT EXISTS idx_messages_session_id ON conversation_messages(session_id);
CREATE INDEX IF NOT EXISTS idx_sessions_updated_at ON conversation_sessions(updated_at DESC);