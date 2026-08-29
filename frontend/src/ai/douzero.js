import { cardStrength } from '@/utils/cards.js'
import { classifyMove } from './legal-moves.js'

export function cardsToArray(cards) {
  const output = new Float32Array(54)
  const counts = new Map()
  for (const card of cards || []) {
    const strength = cardStrength(card.code)
    counts.set(strength, (counts.get(strength) || 0) + 1)
  }
  for (const [strength, count] of counts) {
    if (strength >= 3 && strength <= 15) {
      const column = strength - 3
      for (let index = 0; index < Math.min(4, count); index++) output[column * 4 + index] = 1
    } else if (strength === 16) output[52] = 1
    else if (strength === 17) output[53] = 1
  }
  return output
}

export function playHistory(table) {
  return (table?.actionHistory || [])
    .filter((entry) => entry.action === '出牌' || entry.action === '不出')
    .map((entry) => ({
      seatId: Number(entry.seatId),
      cards: entry.action === '不出' ? [] : entry.cards || [],
    }))
}

export function publicState(table) {
  const history = playHistory(table)
  const playedBySeat = { 0: [], 1: [], 2: [] }
  const lastMoveBySeat = { 0: [], 1: [], 2: [] }
  for (const entry of history) {
    if (entry.seatId < 0 || entry.seatId > 2) continue
    if (entry.cards.length) playedBySeat[entry.seatId].push(...entry.cards)
    lastMoveBySeat[entry.seatId] = entry.cards
  }
  let lastMove = []
  if (history.length) {
    const latest = history.at(-1).cards
    lastMove = latest.length ? latest : history.at(-2)?.cards || []
  } else if (table?.lastPlay?.length) lastMove = table.lastPlay
  const playedCards = history.flatMap((entry) => entry.cards)
  const knownCodes = new Set([...(table?.hand || []), ...playedCards].map((card) => card.code))
  const otherHand = Array.from({ length: 54 }, (_, index) => ({ code: index + 1 })).filter(
    (card) => !knownCodes.has(card.code),
  )
  const playerCount = (seat) => {
    if (seat === Number(table.seatId)) return table.hand?.length || 0
    const player = table.players?.find((candidate) => Number(candidate.seatId) === seat)
    if (player) return Number(player.cardCount)
    const initial = seat === Number(table.landlordSeatId) ? 20 : 17
    return Math.max(0, initial - playedBySeat[seat].length)
  }
  const bombCount = history.filter((entry) => {
    if (entry.cards.length === 2) {
      const ranks = new Set(entry.cards.map((card) => cardStrength(card.code)))
      return ranks.has(16) && ranks.has(17)
    }
    return entry.cards.length === 4 && new Set(entry.cards.map((card) => cardStrength(card.code))).size === 1
  }).length
  return {
    playedBySeat,
    lastMoveBySeat,
    lastMove,
    otherHand,
    playerCount,
    bombCount: Math.min(14, bombCount),
  }
}

export function roleFor(table) {
  const landlord = Number(table.landlordSeatId)
  const seat = Number(table.seatId)
  if (seat === landlord) return 'landlord'
  return (seat - landlord + 3) % 3 === 1 ? 'landlordDown' : 'landlordUp'
}

export function analyzeStateQuality(table) {
  if (table?.stage !== 'PLAYING' || Number(table.landlordSeatId) < 0)
    return { complete: true, expectedPlayed: 0, observedPlayed: 0, delta: 0 }
  const players = table.players || []
  if (players.length < 3)
    return { complete: false, expectedPlayed: -1, observedPlayed: -1, delta: null, reason: 'PLAYERS_MISSING' }
  const landlord = Number(table.landlordSeatId)
  const expectedPlayed = players.reduce((total, player) => {
    const initial = Number(player.seatId) === landlord ? 20 : 17
    return total + Math.max(0, initial - Number(player.cardCount || 0))
  }, 0)
  const observedPlayed = playHistory(table).reduce((total, entry) => total + entry.cards.length, 0)
  const delta = expectedPlayed - observedPlayed
  return {
    complete: delta === 0,
    expectedPlayed,
    observedPlayed,
    delta,
    reason: delta === 0 ? 'COMPLETE' : delta > 0 ? 'HISTORY_TRUNCATED' : 'HISTORY_DUPLICATED',
  }
}

function splitPenalty(hand, move) {
  const handCounts = new Map()
  const moveCounts = new Map()
  for (const card of hand) {
    const rank = cardStrength(card.code)
    handCounts.set(rank, (handCounts.get(rank) || 0) + 1)
  }
  for (const card of move) {
    const rank = cardStrength(card.code)
    moveCounts.set(rank, (moveCounts.get(rank) || 0) + 1)
  }
  let penalty = 0
  for (const [rank, used] of moveCounts) {
    const held = handCounts.get(rank) || 0
    if (used >= held) continue
    if (held === 4) penalty += 55
    else if (held === 3) penalty += 24
    else if (held === 2) penalty += 10
  }
  return penalty
}

function heuristicScore(table, cards) {
  if (!cards.length) return -10
  if (cards.length === table.hand.length) return 100_000
  const pattern = classifyMove(cards)
  const strengths = cards.map((card) => cardStrength(card.code))
  const highCardCost = strengths.reduce((total, strength) => total + Math.max(0, strength - 13) * 3, 0)
  const patternBonus =
    {
      2: 28,
      4: 32,
      6: 36,
      7: 18,
      8: 22,
      9: 42,
      10: 48,
      13: 14,
      14: 18,
    }[pattern?.type] || 0
  const bombCost = pattern?.type === 15 ? 48 : pattern?.type === 25 ? 60 : 0
  return cards.length * 22 + patternBonus - splitPenalty(table.hand, cards) - highCardCost - bombCost
}

export function selectHeuristicCandidate(table, candidates) {
  const winning = candidates.findIndex((candidate) => candidate.length === table.hand.length)
  if (winning >= 0) return winning
  const pass = candidates.findIndex((candidate) => candidate.length === 0)
  const playable = candidates
    .map((cards, index) => ({ cards, index, score: heuristicScore(table, cards) }))
    .filter((candidate) => candidate.cards.length)
    .sort((left, right) => right.score - left.score)
  if (!playable.length) return pass >= 0 ? pass : 0

  const landlord = Number(table.landlordSeatId)
  const lastPlayer = Number(table.lastPlaySeatId)
  const freeTurn = !table.lastPlay?.length || !table.lastPlayType || lastPlayer === Number(table.seatId)
  if (freeTurn) return playable[0].index
  if (lastPlayer !== landlord) return pass >= 0 ? pass : playable[0].index
  return playable[0].index
}

export function evaluateHandStrength(hand) {
  const groups = new Map()
  for (const card of hand || []) {
    const rank = cardStrength(card.code)
    groups.set(rank, (groups.get(rank) || 0) + 1)
  }
  const hasSmall = groups.has(16)
  const hasBig = groups.has(17)
  let score = hasBig ? 4 : 0
  if (hasSmall) score += 3
  if (hasSmall && hasBig) score += 4
  score += [...groups.values()].filter((count) => count === 4).length * 8
  score += (groups.get(15) || 0) * 3
  score += groups.get(14) || 0
  return score
}
