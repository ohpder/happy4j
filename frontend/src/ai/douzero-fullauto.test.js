import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import {
  cardsToScoreArray,
  encodeFullAutoX,
  encodeFullAutoZ,
  FULLAUTO_PLAY_MODELS,
  resolveFullAutoRole,
  selectFullAutoCandidate,
} from './douzero-fullauto.js'

const card = (code) => ({ code })

function tableFixture() {
  return {
    stage: 'PLAYING',
    seatId: 0,
    landlordSeatId: 0,
    hand: [3, 16, 29, 4, 17, 30, 5, 18, 6, 19, 7, 20, 8, 21, 1, 2, 53, 54].map(card),
    bottomCards: [card(1), card(5), card(54)],
    lastPlay: [card(10)],
    lastPlayType: 1,
    lastPlayRank: 8,
    lastPlaySeatId: 2,
    players: [
      { seatId: 0, cardCount: 18 },
      { seatId: 1, cardCount: 16 },
      { seatId: 2, cardCount: 15 },
    ],
    actionHistory: [
      { seatId: 0, action: '出牌', cards: [card(9)] },
      { seatId: 1, action: '不出', cards: [] },
      { seatId: 2, action: '出牌', cards: [card(10)] },
    ],
  }
}

describe('DouZero FullAuto ResNet', () => {
  it('按服务器递增出牌顺序映射地主、地主下家和地主上家模型', () => {
    for (let landlord = 0; landlord < 3; landlord++) {
      expect(resolveFullAutoRole({ seatId: landlord, landlordSeatId: landlord })).toBe('landlord')
      expect(resolveFullAutoRole({ seatId: (landlord + 1) % 3, landlordSeatId: landlord })).toBe(
        'landlord_down',
      )
      expect(resolveFullAutoRole({ seatId: (landlord + 2) % 3, landlordSeatId: landlord })).toBe(
        'landlord_up',
      )
    }
    expect(FULLAUTO_PLAY_MODELS).toEqual({
      landlord: 'landlord.onnx',
      landlord_up: 'landlord_up.onnx',
      landlord_down: 'landlord_down.onnx',
    })
  })

  it('固定目标仓库提交及六个模型资源的哈希', () => {
    const modelRoot = new URL('../../public/models/douzero_fullauto/', import.meta.url)
    const manifest = JSON.parse(readFileSync(new URL('manifest.json', modelRoot), 'utf8'))
    expect(manifest).toMatchObject({
      name: 'DouZero For HLDDZ FullAuto ResNet',
      objective: 'ADP',
      featureSchema: 'douzero-fullauto-resnet-v1',
      sourceCommit: '6d20491da216c989d23cf2883498758fb78e9f8d',
    })
    expect(manifest.models).toHaveLength(6)
    for (const model of manifest.models) {
      const digest = createHash('sha256')
        .update(readFileSync(new URL(model.file, modelRoot)))
        .digest('hex')
      expect(digest).toBe(model.onnxSha256)
    }
  })

  it('生成每个候选 40×54 的牌局矩阵和 15 维角色先验', () => {
    const table = tableFixture()
    const candidates = [[table.hand[0]], []]
    const z = encodeFullAutoZ(table, candidates, 'landlord')
    const x = encodeFullAutoX(candidates, 'landlord')
    expect(z).toHaveLength(2 * 40 * 54)
    expect(x).toHaveLength(2 * 15)
    expect(Array.from(x.slice(0, 13))).toEqual([1, 0.5, 1, 1, 1, 1, 1, 5, -4, 1, 1, 1, 1])
    expect(x[13]).toBeCloseTo(0.8)
    expect(x[14]).toBeCloseTo(1.3)
    expect(z[0]).toBe(1)
    expect(z[54 + 17]).toBe(1)
  })

  it('按 FullAuto 4×15 行优先格式编码局前手牌', () => {
    const encoded = cardsToScoreArray([3, 16, 29, 53, 54].map(card))
    expect(encoded).toHaveLength(60)
    expect(encoded[0]).toBe(1)
    expect(encoded[15]).toBe(1)
    expect(encoded[30]).toBe(1)
    expect(encoded[13]).toBe(1)
    expect(encoded[14]).toBe(1)
  })

  it('在模型略偏向不出但第二候选收益足够时主动压制', () => {
    const table = {
      ...tableFixture(),
      hand: [card(3), card(4), card(5)],
      bottomCards: [],
      actionHistory: [{ seatId: 2, action: '出牌', cards: [card(3)] }],
      lastPlay: [card(3)],
      lastPlayType: 1,
      lastPlayRank: 1,
    }
    const selection = selectFullAutoCandidate(table, [[], [card(4)]], [0.14, 0.13])
    expect(selection).toMatchObject({ best: 1, reason: 'FULLAUTO_ACTIVE_RESPONSE' })
  })
})
