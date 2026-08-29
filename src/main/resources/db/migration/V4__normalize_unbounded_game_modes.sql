UPDATE game_mode_config
SET max_beans = 0,
    updated_at = CURRENT_TIMESTAMP(6),
    version = version + 1
WHERE max_beans < 0;
