CREATE TABLE app_user (
    id BINARY(16) NOT NULL,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_app_user_username UNIQUE (username)
);

CREATE TABLE auth_token (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    last_seen_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_token_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    INDEX idx_auth_token_user_active (user_id, revoked_at, expires_at)
);

CREATE TABLE game_client_session (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    phase VARCHAR(32) NOT NULL,
    detail VARCHAR(512) NOT NULL,
    provider VARCHAR(16) NULL,
    game_uin BIGINT UNSIGNED NULL,
    snapshot_json LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    disconnected_at TIMESTAMP(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_game_session_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    INDEX idx_game_session_owner_updated (user_id, updated_at)
);

CREATE TABLE game_account (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    provider VARCHAR(16) NOT NULL,
    game_uin BIGINT UNSIGNED NOT NULL,
    nickname VARCHAR(128) NOT NULL,
    avatar_url VARCHAR(1024) NOT NULL,
    beans BIGINT NOT NULL DEFAULT 0,
    diamonds BIGINT NOT NULL DEFAULT 0,
    gold_coins BIGINT NOT NULL DEFAULT 0,
    last_login_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_game_account_identity UNIQUE (provider, game_uin),
    CONSTRAINT fk_game_account_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    INDEX idx_game_account_owner (user_id)
);

CREATE TABLE game_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    event_index BIGINT NOT NULL,
    kind VARCHAR(48) NOT NULL,
    message VARCHAR(1024) NOT NULL,
    payload_json LONGTEXT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_game_event_session_index UNIQUE (session_id, event_index),
    CONSTRAINT fk_game_event_session FOREIGN KEY (session_id) REFERENCES game_client_session(id) ON DELETE CASCADE,
    CONSTRAINT fk_game_event_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    INDEX idx_game_event_owner_time (user_id, occurred_at)
);
