INSERT INTO server_config(config_key, value_json, description, updated_at, version) VALUES
('runtime.max-active-users', '64', '单实例允许同时保持 Lotus 登录的最大用户数', CURRENT_TIMESTAMP(6), 0),
('runtime.max-active-sessions-per-user', '1', '每个站点用户允许同时保持的游戏连接数', CURRENT_TIMESTAMP(6), 0),
('runtime.worker-threads', '32', '登录与恢复任务的有界工作线程数', CURRENT_TIMESTAMP(6), 0),
('runtime.worker-queue-capacity', '512', '登录与恢复任务队列容量', CURRENT_TIMESTAMP(6), 0),
('runtime.qr-poll-threads', '8', '服务端二维码轮询线程数', CURRENT_TIMESTAMP(6), 0);
