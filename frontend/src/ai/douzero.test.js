import { describe, expect, it } from 'vitest'
import {
  analyzeStateQuality,
  cardsToArray,
  evaluateHandStrength,
  selectHeuristicCandidate,
} from './douzero.js'

const card = (code) => ({ code })

describe('DouZero 公共特征与后备策略', () => {
  it('使用 54 维列优先 unary 扑克牌编码', () => {
    const encoded = cardsToArray([3, 16, 29, 53, 54].map(card))
    expect(encoded).toHaveLength(54)
    expect(Array.from(encoded.slice(0, 4))).toEqual([1, 1, 1, 0])
    expect(encoded[52]).toBe(1)
    expect(encoded[53]).toBe(1)
  })

  it('复刻参考项目的叫地主手牌强度', () => {
    expect(evaluateHandStrength([53, 54, 2, 15, 1].map(card))).toBe(18)
    expect(evaluateHandStrength([3, 16, 29, 42].map(card))).toBe(8)
  })

  it('检测服务端历史截断，避免把不完整公开牌状态送入模型', () => {
    const complete = {
      stage: 'PLAYING',
      landlordSeatId: 0,
      players: [
        { seatId: 0, cardCount: 18 },
        { seatId: 1, cardCount: 16 },
        { seatId: 2, cardCount: 17 },
      ],
      actionHistory: [
        { seatId: 0, action: '出牌', cards: [card(3), card(16)] },
        { seatId: 1, action: '出牌', cards: [card(4)] },
      ],
    }
    expect(analyzeStateQuality(complete)).toMatchObject({
      complete: true,
      expectedPlayed: 3,
      observedPlayed: 3,
    })
    expect(
      analyzeStateQuality({ ...complete, actionHistory: complete.actionHistory.slice(1) }),
    ).toMatchObject({
      complete: false,
      delta: 2,
      reason: 'HISTORY_TRUNCATED',
    })
  })

  it('公开牌历史不完整时保留组合，跟队友牌时主动让牌', () => {
    const straight = [3, 4, 5, 6, 7].map(card)
    const hand = [...straight, card(20), card(33)]
    const freeTable = { hand, seatId: 0, landlordSeatId: 1, lastPlay: [], lastPlayType: 0 }
    const freeCandidates = [[card(3)], straight, [card(20), card(33)]]
    expect(selectHeuristicCandidate(freeTable, freeCandidates)).toBe(1)

    const responseTable = {
      ...freeTable,
      lastPlay: [card(8)],
      lastPlayType: 1,
      lastPlaySeatId: 2,
    }
    expect(selectHeuristicCandidate(responseTable, [[card(9)], []])).toBe(1)
    expect(selectHeuristicCandidate({ ...responseTable, lastPlaySeatId: 1 }, [[card(9)], []])).toBe(0)
  })
})
