import { describe, expect, it } from 'vitest'
import { buildSuggestions, cardStrength, hasBeatingPlay, remainingCardCounts, sortCards } from './cards.js'

const card = (code) => ({ code })

describe('斗地主手牌工具', () => {
  it('按大王、小王、2、A、K 到 3 从左到右排列', () => {
    expect(sortCards([3, 16, 1, 2, 53, 54, 13].map(card)).map((item) => item.code)).toEqual([
      54, 53, 2, 1, 13, 3, 16,
    ])
    expect(cardStrength(1)).toBe(14)
    expect(cardStrength(3)).toBe(3)
  })

  it('优先提示能压过上家的对子并保留炸弹兜底', () => {
    const hand = [3, 16, 4, 17, 5, 18, 6, 19, 32, 45].map(card)
    const suggestions = buildSuggestions(hand, 3, 1, 2)
    expect(suggestions[0].map((item) => item.code)).toEqual([4, 17])
    expect(suggestions.some((items) => items.length === 4)).toBe(true)
  })

  it('记牌器扣除当前手牌与已经打出的牌', () => {
    const counts = remainingCardCounts({
      hand: [card(3), card(16), card(53)],
      actionHistory: [{ cards: [card(4), card(54)] }],
    })
    expect(counts.find((item) => item.label === '3').count).toBe(2)
    expect(counts.find((item) => item.label === '4').count).toBe(3)
    expect(counts.find((item) => item.label === '小王').count).toBe(0)
    expect(counts.find((item) => item.label === '大王').count).toBe(0)
  })

  it('没有任何可压制牌型时进入原版十秒不出阶段', () => {
    const hand = [3, 16, 4, 17, 5, 18].map(card)
    expect(hasBeatingPlay(hand, 3, 10, 2)).toBe(false)
    expect(hasBeatingPlay([...hand, card(53), card(54)], 3, 10, 2)).toBe(true)
  })
})
