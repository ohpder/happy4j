import { describe, expect, it } from 'vitest'
import { chooseDoubleDecision, chooseLandlordDecision } from './policy.js'

const card = (code) => ({ code })
const weak = [3, 4, 5, 6, 7].map(card)
const medium = [53, 54].map(card)
const strong = [53, 54, 2, 15, 1].map(card)
const dominant = [53, 54, 3, 16, 29, 42, 4, 17, 30, 43].map(card)

describe('AI 叫抢地主与加倍策略', () => {
  it('叫地主和抢地主使用不同的手牌强度阈值', () => {
    expect(chooseLandlordDecision(['CALL_LANDLORD'], medium)).toMatchObject({ accept: false, label: '不叫' })
    expect(chooseLandlordDecision(['CALL_LANDLORD'], strong)).toMatchObject({ accept: true, label: '叫地主' })
    expect(chooseLandlordDecision(['ROB_LANDLORD'], medium)).toMatchObject({ accept: false, label: '不抢' })
    expect(chooseLandlordDecision(['ROB_LANDLORD'], strong)).toMatchObject({ accept: true, label: '抢地主' })
  })

  it('仅在强牌且道具数量足够时选择超级加倍', () => {
    expect(chooseDoubleDecision(['DOUBLE', 'SUPER_DOUBLE'], dominant, 1).command).toBe('SUPER_MULTIPLE')
    expect(chooseDoubleDecision(['DOUBLE', 'SUPER_DOUBLE'], dominant, 0)).toMatchObject({
      command: 'MULTIPLE',
      payload: { accept: true },
    })
    expect(chooseDoubleDecision(['DOUBLE'], weak, 0)).toMatchObject({
      command: 'MULTIPLE',
      payload: { accept: false },
    })
  })
})
