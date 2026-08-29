import { cardStrength } from '@/utils/cards.js'

const TYPE = Object.freeze({
  PASS: 0,
  SINGLE: 1,
  STRAIGHT: 2,
  PAIR: 3,
  PAIR_STRAIGHT: 4,
  TRIPLE: 5,
  TRIPLE_STRAIGHT: 6,
  TRIPLE_SINGLE: 7,
  TRIPLE_PAIR: 8,
  PLANE_SINGLE: 9,
  PLANE_PAIR: 10,
  FOUR_SINGLE: 13,
  FOUR_PAIR: 14,
  BOMB: 15,
  ROCKET: 25,
})

function nativeRank(card) {
  return cardStrength(card.code) - 2
}

function groupByRank(cards) {
  const result = new Map()
  for (const card of cards || []) {
    const rank = nativeRank(card)
    if (!result.has(rank)) result.set(rank, [])
    result.get(rank).push(card)
  }
  return new Map([...result.entries()].sort(([left], [right]) => left - right))
}

function isConsecutive(ranks) {
  if (!ranks.length || ranks.at(-1) >= 13) return false
  return ranks.every((rank, index) => index === 0 || rank === ranks[index - 1] + 1)
}

export function classifyMove(cards) {
  if (!cards?.length) return { type: TYPE.PASS, mainRank: 0, length: 0 }
  const groups = groupByRank(cards)
  const ranks = [...groups.keys()]
  const counts = [...groups.values()].map((group) => group.length)
  const size = cards.length
  const high = ranks.at(-1)
  if (size === 1) return { type: TYPE.SINGLE, mainRank: high, length: size }
  if (size === 2 && groups.has(14) && groups.has(15)) return { type: TYPE.ROCKET, mainRank: 15, length: 2 }
  if (groups.size === 1) {
    if (size === 2) return { type: TYPE.PAIR, mainRank: high, length: size }
    if (size === 3) return { type: TYPE.TRIPLE, mainRank: high, length: size }
    if (size === 4) return { type: TYPE.BOMB, mainRank: high, length: size }
  }
  const rankWithCount = (count) => ranks.filter((rank) => groups.get(rank).length === count).at(-1)
  if (size === 4 && counts.filter((count) => count === 3).length === 1)
    return { type: TYPE.TRIPLE_SINGLE, mainRank: rankWithCount(3), length: size }
  if (size === 5 && counts.filter((count) => count === 3).length === 1 && counts.includes(2))
    return { type: TYPE.TRIPLE_PAIR, mainRank: rankWithCount(3), length: size }
  if (size >= 5 && groups.size === size && isConsecutive(ranks))
    return { type: TYPE.STRAIGHT, mainRank: high, length: size }
  if (size >= 6 && size % 2 === 0 && counts.every((count) => count === 2) && isConsecutive(ranks))
    return { type: TYPE.PAIR_STRAIGHT, mainRank: high, length: size }
  if (size >= 6 && size % 3 === 0 && counts.every((count) => count === 3) && isConsecutive(ranks))
    return { type: TYPE.TRIPLE_STRAIGHT, mainRank: high, length: size }
  const triples = ranks.filter((rank) => groups.get(rank).length === 3)
  if (triples.length >= 2 && isConsecutive(triples)) {
    const wingGroups = ranks.filter((rank) => !triples.includes(rank)).map((rank) => groups.get(rank).length)
    const wingCount = size - triples.length * 3
    if (wingCount === triples.length && wingGroups.every((count) => count === 1))
      return { type: TYPE.PLANE_SINGLE, mainRank: triples.at(-1), length: size }
    if (wingCount === triples.length * 2 && wingGroups.every((count) => count === 2))
      return { type: TYPE.PLANE_PAIR, mainRank: triples.at(-1), length: size }
  }
  if (size === 6 && counts.filter((count) => count === 4).length === 1)
    return { type: TYPE.FOUR_SINGLE, mainRank: rankWithCount(4), length: size }
  if (
    size === 8 &&
    counts.filter((count) => count === 4).length === 1 &&
    ranks.filter((rank) => groups.get(rank).length !== 4).every((rank) => groups.get(rank).length === 2)
  )
    return { type: TYPE.FOUR_PAIR, mainRank: rankWithCount(4), length: size }
  return null
}

function combinations(values, count) {
  const result = []
  const current = []
  const visit = (start) => {
    if (current.length === count) {
      result.push([...current])
      return
    }
    for (let index = start; index <= values.length - (count - current.length); index++) {
      current.push(values[index])
      visit(index + 1)
      current.pop()
    }
  }
  visit(0)
  return result
}

function consecutiveRankRuns(ranks, minimum) {
  const allowed = ranks.filter((rank) => rank < 13)
  const result = []
  for (let start = 0; start < allowed.length; start++) {
    for (let end = start + minimum; end <= allowed.length; end++) {
      const run = allowed.slice(start, end)
      if (isConsecutive(run)) result.push(run)
    }
  }
  return result
}

function cardsForRanks(groups, ranks, width) {
  return ranks.flatMap((rank) => groups.get(rank).slice(0, width))
}

function enumerateAll(hand) {
  const groups = groupByRank(hand)
  const ranks = [...groups.keys()]
  const moves = []
  const append = (cards) => {
    if (classifyMove(cards)) moves.push(cards)
  }

  for (const group of groups.values()) {
    append(group.slice(0, 1))
    if (group.length >= 2) append(group.slice(0, 2))
    if (group.length >= 3) append(group.slice(0, 3))
    if (group.length === 4) append(group.slice(0, 4))
  }

  if (groups.has(14) && groups.has(15)) append([groups.get(14)[0], groups.get(15)[0]])

  for (const [rank, group] of groups) {
    if (group.length < 3) continue
    const body = group.slice(0, 3)
    const wingRanks = ranks.filter((candidate) => candidate !== rank)
    for (const wing of wingRanks) append([...body, groups.get(wing)[0]])
    for (const wing of wingRanks.filter((candidate) => groups.get(candidate).length >= 2))
      append([...body, ...groups.get(wing).slice(0, 2)])
  }

  for (const run of consecutiveRankRuns(
    ranks.filter((rank) => groups.get(rank).length >= 1),
    5,
  ))
    append(cardsForRanks(groups, run, 1))
  for (const run of consecutiveRankRuns(
    ranks.filter((rank) => groups.get(rank).length >= 2),
    3,
  ))
    append(cardsForRanks(groups, run, 2))

  const tripleRuns = consecutiveRankRuns(
    ranks.filter((rank) => groups.get(rank).length >= 3),
    2,
  )
  for (const run of tripleRuns) {
    const body = cardsForRanks(groups, run, 3)
    append(body)
    const wingRanks = ranks.filter((rank) => !run.includes(rank))
    for (const wings of combinations(wingRanks, run.length))
      append([...body, ...wings.map((rank) => groups.get(rank)[0])])
    const pairRanks = wingRanks.filter((rank) => groups.get(rank).length >= 2)
    for (const wings of combinations(pairRanks, run.length))
      append([...body, ...cardsForRanks(groups, wings, 2)])
  }

  for (const [rank, group] of groups) {
    if (group.length !== 4) continue
    const otherRanks = ranks.filter((candidate) => candidate !== rank)
    for (const wings of combinations(otherRanks, 2))
      append([...group, ...wings.map((wing) => groups.get(wing)[0])])
    const pairRanks = otherRanks.filter((candidate) => groups.get(candidate).length >= 2)
    for (const wings of combinations(pairRanks, 2)) append([...group, ...cardsForRanks(groups, wings, 2)])
  }

  const seen = new Set()
  return moves.filter((move) => {
    const key = move
      .map((card) => card.code)
      .sort((left, right) => left - right)
      .join('-')
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

function canBeat(candidate, previous) {
  if (!previous || previous.type === TYPE.PASS || previous.length === 0) return true
  if (candidate.type === TYPE.ROCKET) return previous.type !== TYPE.ROCKET
  if (previous.type === TYPE.ROCKET) return false
  if (candidate.type === TYPE.BOMB && previous.type !== TYPE.BOMB) return true
  return (
    candidate.type === previous.type &&
    candidate.length === previous.length &&
    candidate.mainRank > previous.mainRank
  )
}

export function enumerateAllMoves(hand) {
  return enumerateAll(hand || [])
}

export function canBeatMove(candidate, previous) {
  return canBeat(candidate, previous)
}

export function enumerateLegalMoves(table) {
  const hand = table?.hand || []
  if (!hand.length) return []
  const freeTurn =
    !table.lastPlay?.length || !table.lastPlayType || Number(table.lastPlaySeatId) === Number(table.seatId)
  const previous = freeTurn
    ? null
    : {
        type: Number(table.lastPlayType),
        mainRank: Number(table.lastPlayRank),
        length: table.lastPlay.length,
      }
  const moves = enumerateAll(hand).filter((move) => canBeat(classifyMove(move), previous))
  if (!freeTurn) moves.push([])
  return moves.sort((left, right) => {
    const leftType = classifyMove(left)?.type || 0
    const rightType = classifyMove(right)?.type || 0
    const bombOrder =
      Number([TYPE.BOMB, TYPE.ROCKET].includes(leftType)) -
      Number([TYPE.BOMB, TYPE.ROCKET].includes(rightType))
    if (bombOrder) return bombOrder
    return left.length - right.length
  })
}

export { TYPE, nativeRank }
