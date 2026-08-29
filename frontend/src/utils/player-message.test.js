import { describe, expect, it } from 'vitest'
import { playerMessage } from './player-message.js'

describe('玩家提示文案', () => {
  it('隐藏协议、解码和超时细节', () => {
    expect(playerMessage('invalid protobuf payload for Native.Response')).toBe('数据同步遇到问题，请稍后再试')
    expect(playerMessage('java.util.concurrent.TimeoutException')).toBe('网络响应较慢，请稍后再试')
    expect(playerMessage('Lotus socket connection reset')).toBe('游戏连接已中断，请重新进入')
    expect(playerMessage('服务器拒绝操作，错误码 3')).toBe('当前操作没有成功，请重试')
  })

  it('把任务错误转换成玩家能理解的提示', () => {
    expect(playerMessage('CGI result=1001:')).toBe('任务尚未完成或当前不可领取')
    expect(playerMessage('CGI result=6:')).toBe('当前暂不满足领取条件')
  })

  it('保留可操作的大模型错误提示', () => {
    expect(playerMessage('大模型HTTP 429：该模型当前访问量过大，请您稍后再试')).toBe(
      '当前模型访问量过大，请稍后重试或更换模型',
    )
    expect(playerMessage('大模型HTTP 401：invalid api key')).toBe('API Key 校验失败，请检查后重新保存')
  })
})
