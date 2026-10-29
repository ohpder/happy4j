import { playHistory } from './douzero.js'
import { cardStrength } from '@/utils/cards.js'
import { enumerateLegalMoves } from './legal-moves.js'

const MODEL_ROOT = '/models/perfectdou'
const models = new Map()
let ortPromise
let actionSpacePromise

function runtime() {
  if (!ortPromise)
    ortPromise = import('onnxruntime-web/wasm').then((ort) => {
      ort.env.wasm.numThreads = 1
      return ort
    })
  return ortPromise
}

function actionSpace() {
  if (!actionSpacePromise)
    actionSpacePromise = fetch(`${MODEL_ROOT}/action_space.json`).then((response) => response.json())
  return actionSpacePromise
}

async function model(role) {
  if (!models.has(role)) {
    models.set(
      role,
      Promise.all([
        runtime(),
        fetch(`${MODEL_ROOT}/${role}.onnx`).then((response) => response.arrayBuffer()),
      ]).then(([ort, bytes]) => ort.InferenceSession.create(bytes, { executionProviders: ['wasm'] })),
    )
  }
  return models.get(role)
}

function envRank(code) {
  return cardStrength(code)
}

function label(rank) {
  if (rank <= 10) return String(rank)
  return { 11: 'J', 12: 'Q', 13: 'K', 14: 'A', 15: '2', 16: 'B', 17: 'R' }[rank] || ''
}

function cardFeature(target, offset, cards) {
  const counts = new Map()
  for (const card of cards || []) {
    const rank = envRank(typeof card === 'number' ? card : card.code)
    counts.set(rank, (counts.get(rank) || 0) + 1)
  }
  for (const [rank, count] of counts) {
    const column = rank >= 3 && rank <= 17 ? rank - 3 : -1
    if (column < 0 || column >= 15) continue
    for (let row = 0; row < Math.min(4, count); row++) target[offset + row * 15 + column] = 1
    if (count >= 1) target[offset + 60 + column] = 1
    if (count >= 2) target[offset + 75 + column] = 1
    if (count >= 3) target[offset + 90 + column] = 1
    if (count >= 4) target[offset + 105 + column] = 1
  }
}

function seatRole(table) {
  if (Number(table.seatId) === Number(table.landlordSeatId)) return 'landlord'
  return Number(table.seatId) === (Number(table.landlordSeatId) + 1) % 3 ? 'landlord_down' : 'landlord_up'
}

function playerCount(table, seat, played) {
  if (seat === Number(table.seatId)) return table.hand?.length || 0
  const player = table.players?.find((item) => Number(item.seatId) === seat)
  return Number(
    player?.cardCount ??
      Math.max(0, (seat === Number(table.landlordSeatId) ? 20 : 17) - (played[seat]?.length || 0)),
  )
}

function state(table) {
  const history = playHistory(table)
  const played = { 0: [], 1: [], 2: [] }
  history.forEach((entry) => {
    if (entry.seatId >= 0 && entry.seatId < 3) played[entry.seatId].push(...entry.cards)
  })
  const landlord = Number(table.landlordSeatId)
  const up = (landlord + 2) % 3
  const down = (landlord + 1) % 3
  const known = new Set([
    ...(table.hand || []).map((card) => card.code),
    ...history.flatMap((entry) => entry.cards.map((card) => card.code)),
  ])
  const notPlayed = Array.from({ length: 54 }, (_, index) => index + 1).filter((code) => !known.has(code))
  const lastMove = table.lastPlay || history.at(-1)?.cards || []
  return {
    history: history.map((entry) => entry.cards.map((card) => card.code)),
    current: (table.hand || []).map((card) => card.code),
    notPlayed,
    played,
    up,
    down,
    lastMove: lastMove.map((card) => card.code),
    counts: {
      current: playerCount(table, table.seatId, played),
      up: playerCount(table, up, played),
      down: playerCount(table, down, played),
    },
  }
}

function actionKey(cards) {
  return cards
    .map((code) => label(envRank(code)))
    .sort()
    .join('')
}

function encode(table, candidates, actions) {
  const current = state(table)
  const input = new Float32Array(32738)
  cardFeature(input, 0, current.up)
  cardFeature(input, 180, [])
  cardFeature(input, 360, current.current)
  cardFeature(input, 540, current.notPlayed)
  const recent = current.history.slice(-15)
  recent.forEach((cards, index) => cardFeature(input, (4 + index) * 180, cards))
  cardFeature(input, 19 * 180, current.played[table.seatId] || [])
  cardFeature(input, 20 * 180, current.played[current.up] || [])
  cardFeature(input, 21 * 180, current.played[current.down] || [])
  cardFeature(input, 22 * 180, [])
  cardFeature(input, 23 * 180, [])
  cardFeature(input, 24 * 180, current.lastMove)
  cardFeature(
    input,
    25 * 180,
    (table.bottomCards || []).map((card) => card.code),
  )
  input[4680] = current.counts.up / 17
  input[4681] = current.counts.down / 20
  input[4682] = current.counts.current / 20
  input[4683] = Object.values(current.played).flat().length / 54
  input[4684] = Number(table.multiplier || 1) / 16
  input[4685] = current.counts.current / 20
  input[4687] = 1
  actions.forEach((action, index) => {
    if (index >= 150) return
    const offset = 4688 + index * 187
    input[offset] = action.id
    input[offset + 1] = action.bomb ? 1 : 0
    input[offset + 5] =
      Math.max(0, current.counts.current - action.cards.length) / Math.max(1, current.counts.current)
    input[offset + 6] = 1
    cardFeature(input, offset + 7, action.cards)
  })
  return input
}

export async function choosePerfectDouMove(table, candidates) {
  const role = seatRole(table)
  const space = await actionSpace()
  const actions = candidates.map((cards) => {
    const envCards = cards.map((card) => card.code)
    const key = actionKey(envCards)
    const bomb =
      envCards.length === 4 ||
      (envCards.length === 2 &&
        envCards.some((code) => cardStrength(code) === 16) &&
        envCards.some((code) => cardStrength(code) === 17))
    return { id: space[key] ?? (envCards.length ? 0 : 620), cards: envCards, bomb }
  })
  const ort = await runtime()
  const session = await model(role)
  const input = encode(table, candidates, actions)
  const output = await session.run({ [session.inputNames[0]]: new ort.Tensor('float32', input, [1, 32738]) })
  const values = output.action_logit?.data || output[session.outputNames[0]].data
  let selected = 0
  let score = -Infinity
  actions.slice(0, 150).forEach((action, index) => {
    const value = Number(values[action.id] ?? -Infinity)
    if (value > score) {
      score = value
      selected = index
    }
  })
  return {
    cards: candidates[selected] || [],
    pass: !(candidates[selected] || []).length,
    candidates: candidates.length,
    score,
  }
}

export function perfectDouLegalMoves(table) {
  return enumerateLegalMoves(table)
}
