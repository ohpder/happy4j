ALTER TABLE app_user ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER' AFTER display_name;

CREATE TABLE server_config (
    config_key VARCHAR(128) NOT NULL,
    value_json LONGTEXT NOT NULL,
    description VARCHAR(512) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (config_key)
);

CREATE TABLE mission_definition (
    mission_id INT NOT NULL,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(1024) NOT NULL,
    source VARCHAR(32) NOT NULL,
    payload_json LONGTEXT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_synced_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (mission_id),
    INDEX idx_mission_active_sync (active, last_synced_at)
);

CREATE TABLE game_mode_config (
    scene_id INT NOT NULL,
    family VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    min_beans BIGINT NOT NULL,
    max_beans BIGINT NOT NULL,
    cap BIGINT NOT NULL,
    base_score INT NOT NULL,
    enabled BOOLEAN NOT NULL,
    source VARCHAR(32) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY(scene_id),
    INDEX idx_game_mode_enabled(enabled, scene_id)
);

CREATE TABLE game_login_state (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    provider VARCHAR(16) NOT NULL,
    game_uin BIGINT UNSIGNED NOT NULL,
    encrypted_payload LONGBLOB NOT NULL,
    iv BINARY(12) NOT NULL,
    key_version INT NOT NULL,
    status VARCHAR(24) NOT NULL,
    issued_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    last_used_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_login_state_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    INDEX idx_login_state_user_status (user_id, status, expires_at),
    INDEX idx_login_state_identity (provider, game_uin)
);

INSERT INTO server_config(config_key, value_json, description, updated_at, version) VALUES
('mission.seed-ids', '[1563,684,2662,2666,2671,2673,2816,2817,2818,2820,2821,2822,2823,2824,2825,2826,3170,4160,4161,4162,4163,4168,4169,4170,4171,4172,4173]', '远端任务目录不可用时的数据库回退 ID；在线同步成功后不再依赖此项', CURRENT_TIMESTAMP(6), 0),
('mission.sync-range', '{"begin":1,"end":32767}', 'MissionSvr 7304 在线任务扫描范围', CURRENT_TIMESTAMP(6), 0),
('mission.catalog-ttl-seconds', '900', '在线任务目录刷新周期', CURRENT_TIMESTAMP(6), 0),
('session.restore-enabled', 'true', '允许从加密数据库状态恢复 Lotus 登录', CURRENT_TIMESTAMP(6), 0);
