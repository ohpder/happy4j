const replacements = [
  [/大模型HTTP\s*429.*访问量过大.*/i, '当前模型访问量过大，请稍后重试或更换模型'],
  [/大模型HTTP\s*429.*/i, '大模型请求过于频繁，请稍后重试'],
  [/大模型HTTP\s*401.*/i, 'API Key 校验失败，请检查后重新保存'],
  [/大模型HTTP\s*403.*/i, '当前 API Key 无权调用这个模型'],
  [/大模型HTTP\s*404.*/i, '模型或 API URL 不存在，请检查配置'],
  [/CGI result=1001.*/i, '任务尚未完成或当前不可领取'],
  [/CGI result=6.*/i, '当前暂不满足领取条件'],
  [/(TimeoutException|请求超时|timed out).*/i, '网络响应较慢，请稍后再试'],
  [/(invalid protobuf|TDR|payload|schema|decode|解码失败).*/i, '数据同步遇到问题，请稍后再试'],
  [/(Lotus|GameSvr|gRPC|socket|connection reset).*/i, '游戏连接已中断，请重新进入'],
  [/(15806|断线重回).*/i, '正在恢复牌局，请稍候'],
  [/(Exception|Native\.|Tdrpb|QuickGame|SafeLogin|OAuth|HTTP\s*\d+).*/i, '操作没有完成，请稍后再试'],
  [/(错误码|服务器拒绝|返回码|result=).*/i, '当前操作没有成功，请重试'],
]

export function playerMessage(value) {
  const message = String(value || '').trim()
  if (!message) return '操作没有完成，请稍后再试'
  for (const [pattern, replacement] of replacements) {
    if (pattern.test(message)) return replacement
  }
  const server = message.match(/游戏服务器返回\s*-?\d+[:：]?\s*(.*)/)
  if (server) return server[1]?.replace(/^['"]|['"]$/g, '') || '暂时无法进入该场次'
  if (
    message.length > 72 ||
    ['{', '}', '[', ']', '<', '>', '_', '='].some((token) => message.includes(token))
  )
    return '操作没有完成，请稍后再试'
  return message
}
