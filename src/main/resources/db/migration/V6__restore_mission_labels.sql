UPDATE mission_definition
SET title = CASE mission_id
    WHEN 1563 THEN '每日欢乐豆补助'
    WHEN 2666 THEN '完成不洗牌玩法对局'
    WHEN 2671 THEN '完成七雀牌玩法对局'
    WHEN 2673 THEN '完成自选牌玩法对局'
    WHEN 2816 THEN '购买对局道具'
    WHEN 2817 THEN '参加排位玩法'
    WHEN 2818 THEN '参加欢乐赛事'
    WHEN 2820 THEN '提升排位等级'
    WHEN 2821 THEN '提升头衔等级'
    WHEN 2822 THEN '担任地主'
    WHEN 2823 THEN '参与欢乐寻宝'
    WHEN 2824 THEN '参加夺宝活动'
    WHEN 2825 THEN '使用经验道具'
    WHEN 2826 THEN '完成 2v2 玩法对局'
    WHEN 4160 THEN '查看欢乐手册'
    WHEN 4161 THEN '领取欢乐手册奖励'
    WHEN 4162 THEN '参与时装寻宝'
    WHEN 4163 THEN '购买欢乐手册'
    WHEN 4168 THEN '累计完成对局'
    WHEN 4169 THEN '累计赢取欢乐豆'
    WHEN 4170 THEN '完成经典玩法对局'
    WHEN 4171 THEN '完成欢乐经典玩法对局'
    WHEN 4172 THEN '完成不洗牌玩法对局'
    WHEN 4173 THEN '完成连炸玩法对局'
    ELSE title
END,
version = version + 1
WHERE title = CONCAT('任务 ', mission_id);
