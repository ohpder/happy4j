CREATE TABLE ai_configuration (
    user_id BINARY(16) NOT NULL,
    scene_id INT NOT NULL,
    auto_change_table BOOLEAN NOT NULL DEFAULT TRUE,
    auto_claim_beans BOOLEAN NOT NULL DEFAULT TRUE,
    stop_at_beans BIGINT NOT NULL DEFAULT 0,
    stop_below_beans BIGINT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_ai_configuration_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);
