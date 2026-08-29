import { describe, expect, it } from 'vitest'
import { itemCount, SUPER_DOUBLE_ITEM_ID } from './inventory.js'

describe('牌桌道具数量', () => {
  it('按服务端背包显示超级加倍卡数量', () => {
    const session = { inventory: [{ itemId: SUPER_DOUBLE_ITEM_ID, count: 3 }] }
    expect(itemCount(session, SUPER_DOUBLE_ITEM_ID)).toBe(3)
    expect(itemCount({ inventory: [] }, SUPER_DOUBLE_ITEM_ID)).toBe(0)
  })
})
