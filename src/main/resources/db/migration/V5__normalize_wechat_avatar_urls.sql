UPDATE game_account
SET avatar_url = CONCAT(avatar_url, '/132'),
    updated_at = CURRENT_TIMESTAMP(6),
    version = version + 1
WHERE avatar_url LIKE 'https://wx.qlogo.cn/mmhead/%'
  AND avatar_url NOT LIKE '%/0'
  AND avatar_url NOT LIKE '%/46'
  AND avatar_url NOT LIKE '%/64'
  AND avatar_url NOT LIKE '%/96'
  AND avatar_url NOT LIKE '%/132';
