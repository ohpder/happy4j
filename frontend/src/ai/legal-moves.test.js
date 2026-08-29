import { describe, expect, it } from 'vitest'
import { classifyMove, enumerateLegalMoves, TYPE } from './legal-moves.js'

const card = (code) => ({ code })

describe('DouZero 合法动作枚举', () => {
  it('识别火箭、炸弹与顺子', () => {
    expect(classifyMove([card(53), card(54)]).type).toBe(TYPE.ROCKET)
    expect(classifyMove([3, 16, 29, 42].map(card))).toMatchObject({ type: TYPE.BOMB, mainRank: 1 })
    expect(classifyMove([3, 4, 5, 6, 7].map(card))).toMatchObject({ type: TYPE.STRAIGHT, mainRank: 5 })
  })

  it('跟对时只保留可压制动作并包含不出', () => {
    const table = {
      seatId: 0,
      lastPlaySeatId: 2,
      lastPlayType: TYPE.PAIR,
      lastPlayRank: 1,
      lastPlay: [card(3), card(16)],
      hand: [3, 16, 4, 17, 5, 18, 53, 54].map(card),
    }
    const moves = enumerateLegalMoves(table)
    expect(moves.some((move) => move.length === 0)).toBe(true)
    expect(moves.some((move) => move.length === 2 && move.every((item) => [4, 17].includes(item.code)))).toBe(
      true,
    )
    expect(moves.some((move) => classifyMove(move)?.type === TYPE.ROCKET)).toBe(true)
    expect(moves.some((move) => move.length === 2 && move.every((item) => [3, 16].includes(item.code)))).toBe(
      false,
    )
  })

  it('自由出牌枚举飞机带单且不允许不出', () => {
    const hand = [3, 16, 29, 4, 17, 30, 5, 6, 53, 54].map(card)
    const moves = enumerateLegalMoves({ seatId: 0, lastPlay: [], lastPlayType: 0, hand })
    expect(moves.some((move) => classifyMove(move)?.type === TYPE.PLANE_SINGLE)).toBe(true)
    expect(moves.some((move) => move.length === 0)).toBe(false)
  })

  it('复现真实牌局：多数回合确实无牌可压，但对 9 的对子能找到 10 的对子', () => {
    const hand = [28, 40, 52, 51, 37, 10, 36, 49, 48, 21, 33, 45, 18, 31, 16, 29, 42].map(card)
    const noBeat = enumerateLegalMoves({
      seatId: 2,
      lastPlaySeatId: 0,
      lastPlayType: TYPE.PAIR,
      lastPlayRank: 9,
      lastPlay: [card(11), card(24)],
      hand,
    })
    expect(noBeat).toEqual([[]])

    const canBeat = enumerateLegalMoves({
      seatId: 2,
      lastPlaySeatId: 0,
      lastPlayType: TYPE.PAIR,
      lastPlayRank: 7,
      lastPlay: [card(9), card(22)],
      hand,
    })
    expect(canBeat.some((move) => move.map((item) => item.code).join(',') === '10,36')).toBe(true)
    expect(canBeat.some((move) => move.length === 0)).toBe(true)
  })
})
