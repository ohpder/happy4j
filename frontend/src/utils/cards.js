export const RANK_LABELS = ['3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K', 'A', '2', '小王', '大王']

export function cardStrength(code) {
  if (code === 54) return 17
  if (code === 53) return 16
  const rank = ((code - 1) % 13) + 1
  if (rank === 1) return 14
  if (rank === 2) return 15
  return rank
}

export function sortCards(cards) {
  return [...(cards || [])].sort((left, right) => {
    const rank = cardStrength(right.code) - cardStrength(left.code)
    return rank || left.code - right.code
  })
}

function groups(hand) {
  const result = new Map()
  for (const card of sortCards(hand)) {
    const rank = cardStrength(card.code)
    if (!result.has(rank)) result.set(rank, [])
    result.get(rank).push(card)
  }
  return result
}

function serverRankToStrength(rank) {
  return rank > 0 ? rank + 2 : 0
}

function ascendingGroups(hand) {
  return [...groups(hand).entries()].sort(([left], [right]) => left - right)
}

function sameKindSuggestions(hand, type, previousStrength, count) {
  const values = ascendingGroups(hand)
  const results = []
  const appendGroups = (size) => {
    for (const [rank, cards] of values) {
      if (rank > previousStrength && cards.length >= size) results.push(cards.slice(0, size))
    }
  }
  if (type === 1) appendGroups(1)
  if (type === 3) appendGroups(2)
  if (type === 5) appendGroups(3)
  if (type === 15) appendGroups(4)

  const sequence = (width, minimumCards) => {
    const length = count / width
    if (!Number.isInteger(length) || count < minimumCards) return
    for (let start = 3; start + length - 1 <= 14; start++) {
      const end = start + length - 1
      if (end <= previousStrength) continue
      const selected = []
      let valid = true
      for (let rank = start; rank <= end; rank++) {
        const cards = groups(hand).get(rank) || []
        if (cards.length < width) valid = false
        else selected.push(...cards.slice(0, width))
      }
      if (valid) results.push(selected)
    }
  }
  if (type === 2) sequence(1, 5)
  if (type === 4) sequence(2, 6)
  if (type === 6) sequence(3, 6)

  if (type === 7 || type === 8) {
    const wingSize = type === 7 ? 1 : 2
    for (const [rank, triple] of values) {
      if (rank <= previousStrength || triple.length < 3) continue
      const wing = values.find(([candidate, cards]) => candidate !== rank && cards.length >= wingSize)
      if (wing) results.push([...triple.slice(0, 3), ...wing[1].slice(0, wingSize)])
    }
  }

  if (type === 9 || type === 10) {
    const tripleCount = count / (type === 9 ? 4 : 5)
    if (Number.isInteger(tripleCount) && tripleCount >= 2) {
      for (let start = 3; start + tripleCount - 1 <= 14; start++) {
        const end = start + tripleCount - 1
        if (end <= previousStrength) continue
        const tripleRanks = Array.from({ length: tripleCount }, (_, index) => start + index)
        if (!tripleRanks.every((rank) => (groups(hand).get(rank) || []).length >= 3)) continue
        const triples = tripleRanks.flatMap((rank) => groups(hand).get(rank).slice(0, 3))
        const wings = values.filter(([rank]) => !tripleRanks.includes(rank))
        if (type === 9) {
          const singles = wings.flatMap(([, cards]) => cards.slice(0, 1)).slice(0, tripleCount)
          if (singles.length === tripleCount) results.push([...triples, ...singles])
        } else {
          const pairs = wings
            .filter(([, cards]) => cards.length >= 2)
            .slice(0, tripleCount)
            .flatMap(([, cards]) => cards.slice(0, 2))
          if (pairs.length === tripleCount * 2) results.push([...triples, ...pairs])
        }
      }
    }
  }

  if (type === 13 || type === 14) {
    for (const [rank, bomb] of values) {
      if (rank <= previousStrength || bomb.length < 4) continue
      const others = values.filter(([candidate]) => candidate !== rank)
      if (type === 13) {
        const singles = others.flatMap(([, cards]) => cards).slice(0, 2)
        if (singles.length === 2) results.push([...bomb.slice(0, 4), ...singles])
      } else {
        const pairs = others
          .filter(([, cards]) => cards.length >= 2)
          .slice(0, 2)
          .flatMap(([, cards]) => cards.slice(0, 2))
        if (pairs.length === 4) results.push([...bomb.slice(0, 4), ...pairs])
      }
    }
  }
  return results
}

export function buildSuggestions(hand, lastPlayType = 0, lastPlayRank = 0, lastPlayCount = 0) {
  if (!hand?.length) return []
  const values = ascendingGroups(hand)
  if (!lastPlayType || !lastPlayCount) {
    const lowest = values[0]?.[1]?.[0]
    return lowest ? [[lowest]] : []
  }
  const previousStrength = serverRankToStrength(lastPlayRank)
  const results = sameKindSuggestions(hand, lastPlayType, previousStrength, lastPlayCount)
  if (lastPlayType !== 25) {
    for (const [, cards] of values) {
      if (cards.length >= 4 && (lastPlayType !== 15 || cardStrength(cards[0].code) > previousStrength)) {
        results.push(cards.slice(0, 4))
      }
    }
    const small = hand.find((card) => card.code === 53)
    const big = hand.find((card) => card.code === 54)
    if (small && big) results.push([small, big])
  }
  const seen = new Set()
  return results.filter((cards) => {
    const key = cards
      .map((card) => card.code)
      .sort((a, b) => a - b)
      .join('-')
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

const KNOWN_RESPONSE_TYPES = new Set([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 13, 14, 15, 25])

export function hasBeatingPlay(hand, lastPlayType = 0, lastPlayRank = 0, lastPlayCount = 0) {
  if (!lastPlayType || !lastPlayCount) return Boolean(hand?.length)
  if (!KNOWN_RESPONSE_TYPES.has(lastPlayType)) return true
  return buildSuggestions(hand, lastPlayType, lastPlayRank, lastPlayCount).length > 0
}

export function remainingCardCounts(table) {
  const remaining = new Map(RANK_LABELS.map((label, index) => [index + 3, index >= 13 ? 1 : 4]))
  const remove = (card) => {
    const rank = cardStrength(card.code)
    remaining.set(rank, Math.max(0, (remaining.get(rank) || 0) - 1))
  }
  for (const card of table?.hand || []) remove(card)
  for (const action of table?.actionHistory || []) {
    for (const card of action.cards || []) remove(card)
  }
  return RANK_LABELS.map((label, index) => ({ label, count: remaining.get(index + 3) || 0 }))
}

export function isRedCard(card) {
  return card?.suit === '♥' || card?.suit === '♦'
}
