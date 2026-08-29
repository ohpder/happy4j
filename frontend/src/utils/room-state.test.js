import { describe, expect, it } from 'vitest'
import {
  isRoundResultSnapshot,
  isTableTimeoutSnapshot,
  rightClickAction,
  shouldTrustOnTimeout,
  trustControlAction,
} from './room-state.js'

describe('牌局结算快照', () => {
  it('只为真实结算事件显示结算面板', () => {
    expect(isRoundResultSnapshot({ stage: 'SETTLED', settlement: { players: [] }, actionHistory: [] })).toBe(
      false,
    )
    expect(
      isRoundResultSnapshot({
        stage: 'SETTLED',
        settlement: { players: [{}, {}, {}] },
        actionHistory: [],
      }),
    ).toBe(true)
    expect(
      isRoundResultSnapshot({
        stage: 'SETTLED',
        settlement: { players: [] },
        actionHistory: [{ action: '本局结束' }],
      }),
    ).toBe(true)
  })

  it('对局中始终提供托管或取消托管', () => {
    expect(trustControlAction({ stage: 'PLAYING', trusted: false, availableActions: [] })).toBe('TRUST')
    expect(trustControlAction({ stage: 'PLAYING', trusted: true, availableActions: [] })).toBe('CANCEL_TRUST')
    expect(trustControlAction({ stage: 'DOUBLING', trusted: false })).toBeNull()
  })

  it('识别独立的牌桌超时弹窗状态', () => {
    expect(isTableTimeoutSnapshot({ stage: 'TIMED_OUT' })).toBe(true)
    expect(isTableTimeoutSnapshot({ stage: 'SETTLED' })).toBe(false)
  })

  it('右键只提交已选牌或当前必须的不出', () => {
    const table = { availableActions: ['PLAY', 'PASS', 'TRUST'] }
    expect(rightClickAction(table, 2, false)).toBe('PLAY')
    expect(rightClickAction(table, 0, true)).toBe('PASS')
    expect(rightClickAction(table, 0, false)).toBeNull()
  })

  it('自动托管仅在本机回合真正超时后触发', () => {
    const table = { stage: 'PLAYING', trusted: false, availableActions: ['PLAY', 'TRUST'] }
    expect(shouldTrustOnTimeout(table, 0, true)).toBe(true)
    expect(shouldTrustOnTimeout(table, 1, true)).toBe(false)
    expect(shouldTrustOnTimeout(table, 0, false)).toBe(false)
    expect(shouldTrustOnTimeout({ ...table, trusted: true }, 0, true)).toBe(false)
  })
})
