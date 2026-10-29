import { cardStrength, hasBeatingPlay } from '@/utils/cards.js'
import {
  analyzeStateQuality,
  cardsToArray,
  playHistory,
  publicState,
  roleFor,
  selectHeuristicCandidate,
} from './douzero.js'
import { classifyMove, enumerateAllMoves, enumerateLegalMoves, TYPE } from './legal-moves.js'

export const FULLAUTO_MODEL_NAME = 'DouZero FullAuto ResNet ADP'
export const FULLAUTO_MODEL_SOURCE = 'Vincentzyx/DouZero_For_HLDDZ_FullAuto'

const MODEL_ROOT = '/models/douzero_fullauto'
export const PERFECTDOU_MODEL_ROOT = '/models/perfectdou'
export const PERFECTDOU_MODELS = Object.freeze({
  landlord: 'landlord.onnx',
  landlord_up: 'landlord_up.onnx',
  landlord_down: 'landlord_down.onnx',
})
export const FULLAUTO_PLAY_MODELS = Object.freeze({
  landlord: 'landlord.onnx',
  landlord_up: 'landlord_up.onnx',
  landlord_down: 'landlord_down.onnx',
})
const PRE_GAME_MODELS = Object.freeze({
  bid: 'bid.onnx',
  farmer_up: 'farmer_up.onnx',
  farmer_down: 'farmer_down.onnx',
})
const sessions = new Map()
let ortPromise = null

const ROLE_PRIORS = Object.freeze({
  landlord: {
    bid: [1, 0.5, 1, 1, 1, 1, 1, 5, -4, 1, 1, 1],
    multiply: [1, 0.8, 1.3],
  },
  landlord_up: {
    bid: [1, 0.2, 1, 1, 3.5, 1, 1, 5, 4, 1.035, 1, 0.15],
    multiply: [1, 2.5, 1.3],
  },
  landlord_down: {
    bid: [1, 0.2, 1, 1, 3.5, 1, 1, 5, 4, 1.035, 1, 0.15],
    multiply: [1, 2.5, 1.3],
  },
})

async function ortRuntime() {
  if (!ortPromise) {
    ortPromise = import('onnxruntime-web/wasm').then((ort) => {
      ort.env.wasm.numThreads = 1
      return ort
    })
  }
  return ortPromise
}

async function modelSession(kind, name, onStatus) {
  const key = `${kind}:${name}`
  if (sessions.has(key)) return sessions.get(key)
  onStatus?.('LOADING', `正在加载 FullAuto ${name} 模型`)
  const file = kind === 'play' ? FULLAUTO_PLAY_MODELS[name] : PRE_GAME_MODELS[name]
  const promise = ortRuntime()
    .then((ort) =>
      ort.InferenceSession.create(`${MODEL_ROOT}/${file}`, {
        executionProviders: ['wasm'],
        graphOptimizationLevel: 'all',
        executionMode: 'sequential',
      }),
    )
    .then((session) => {
      onStatus?.('READY', `${FULLAUTO_MODEL_NAME} 已就绪`)
      return session
    })
    .catch((error) => {
      sessions.delete(key)
      onStatus?.('ERROR', 'FullAuto 模型加载失败')
      throw error
    })
  sessions.set(key, promise)
  return promise
}

export function resolveFullAutoRole(table) {
  return (
    {
      landlord: 'landlord',
      landlordUp: 'landlord_up',
      landlordDown: 'landlord_down',
    }[roleFor(table)] || 'landlord'
  )
}

function roleSeats(table) {
  const landlord = Number(table.landlordSeatId)
  return {
    landlord,
    landlord_up: (landlord + 2) % 3,
    landlord_down: (landlord + 1) % 3,
  }
}

function oneHot(value, length) {
  const output = new Float32Array(length)
  const index = Number(value) - 1
  if (index >= 0 && index < length) output[index] = 1
  return output
}

function remainingBottomCards(table, state, seats) {
  const remaining = [...(table.bottomCards || [])]
  for (const played of state.playedBySeat[seats.landlord] || []) {
    const index = remaining.findIndex((card) => cardStrength(card.code) === cardStrength(played.code))
    if (index >= 0) remaining.splice(index, 1)
  }
  return remaining
}

function countRow(table, state, seats) {
  const output = new Float32Array(54)
  output.set(oneHot(state.playerCount(seats.landlord), 20), 0)
  output.set(oneHot(state.playerCount(seats.landlord_up), 17), 20)
  output.set(oneHot(state.playerCount(seats.landlord_down), 17), 37)
  return output
}

function historyRows(table) {
  const history = playHistory(table).slice(-32).reverse()
  const padding = Array.from({ length: 32 - history.length }, () => null)
  return padding.concat(history).map((entry) => {
    if (entry) return cardsToArray(entry.cards)
    const unused = new Float32Array(54)
    unused.fill(-1)
    return unused
  })
}

export function encodeFullAutoZ(table, candidates, _role = resolveFullAutoRole(table)) {
  const state = publicState(table)
  const seats = roleSeats(table)
  const shared = [
    countRow(table, state, seats),
    cardsToArray(table.hand),
    cardsToArray(state.otherHand),
    cardsToArray(remainingBottomCards(table, state, seats)),
    cardsToArray(state.playedBySeat[seats.landlord]),
    cardsToArray(state.playedBySeat[seats.landlord_up]),
    cardsToArray(state.playedBySeat[seats.landlord_down]),
    ...historyRows(table),
  ]
  if (shared.length !== 39) throw new Error(`FullAuto 公共特征行数异常: ${shared.length}`)
  const output = new Float32Array(candidates.length * 40 * 54)
  candidates.forEach((candidate, batch) => {
    let offset = batch * 40 * 54
    output.set(cardsToArray(candidate), offset)
    offset += 54
    for (const row of shared) {
      output.set(row, offset)
      offset += 54
    }
  })
  return output
}

export function encodeFullAutoX(candidates, role) {
  const prior = ROLE_PRIORS[role] || ROLE_PRIORS.landlord
  const row = new Float32Array([...prior.bid, ...prior.multiply])
  const output = new Float32Array(candidates.length * row.length)
  candidates.forEach((_, index) => output.set(row, index * row.length))
  return output
}

export function cardsToScoreArray(cards) {
  const output = new Float32Array(60)
  const counts = new Map()
  for (const card of cards || []) {
    const vector = cardsToArray([card])
    const feature = vector.findIndex((value) => value)
    const column = feature < 52 ? Math.floor(feature / 4) : feature - 39
    counts.set(column, (counts.get(column) || 0) + 1)
  }
  for (const [column, count] of counts)
    for (let row = 0; row < Math.min(4, count); row++) output[row * 15 + column] = 1
  return output
}

async function scoreHand(model, hand, onStatus) {
  const ort = await ortRuntime()
  const session = await modelSession('pre-game', model, onStatus)
  const outputs = await session.run({
    cards: new ort.Tensor('float32', cardsToScoreArray(hand), [1, 60]),
  })
  const tensor = outputs.score || outputs[session.outputNames[0]]
  return Number(tensor.data[0])
}

async function scorePlayCandidates(table, candidates, role, session, ort) {
  const values = []
  const batchSize = 64
  for (let start = 0; start < candidates.length; start += batchSize) {
    const batch = candidates.slice(start, start + batchSize)
    const outputs = await session.run({
      z: new ort.Tensor('float32', encodeFullAutoZ(table, batch, role), [batch.length, 40, 54]),
      x: new ort.Tensor('float32', encodeFullAutoX(batch, role), [batch.length, 15]),
    })
    const tensor = outputs.values || outputs[session.outputNames[0]]
    values.push(...Array.from(tensor.data, Number))
  }
  return values
}

function removeCards(hand, move) {
  const selected = new Set(move.map((card) => card.code))
  return hand.filter((card) => !selected.has(card.code))
}

function findControlPath(table, candidates) {
  const otherHand = publicState(table).otherHand
  const started = performance.now()
  let visited = 0
  const deadline = 8
  const canControl = (move) => {
    const pattern = classifyMove(move)
    return (
      pattern &&
      ![TYPE.FOUR_SINGLE, TYPE.FOUR_PAIR].includes(pattern.type) &&
      !hasBeatingPlay(otherHand, pattern.type, pattern.mainRank, pattern.length)
    )
  }
  const visit = (hand, path) => {
    if (++visited > 240 || performance.now() - started > deadline || path.length >= 6) return null
    const moves = enumerateAllMoves(hand)
      .filter(canControl)
      .sort((left, right) => right.length - left.length)
    for (const move of moves) {
      const remaining = removeCards(hand, move)
      if (!remaining.length) return [...path, move]
      const result = visit(remaining, [...path, move])
      if (result) return result
    }
    return null
  }
  for (let index = 0; index < candidates.length; index++) {
    if (performance.now() - started > deadline) break
    const move = candidates[index]
    if (!move.length || !canControl(move)) continue
    const remaining = removeCards(table.hand, move)
    if (!remaining.length || visit(remaining, [move])) return index
  }
  return -1
}

export function selectFullAutoCandidate(table, candidates, values) {
  const ranked = values
    .map((q, index) => ({ index, q, cards: candidates[index], pattern: classifyMove(candidates[index]) }))
    .sort((left, right) => right.q - left.q)
  const immediate = ranked.find(
    (candidate) =>
      candidate.cards.length === table.hand.length &&
      ![TYPE.FOUR_SINGLE, TYPE.FOUR_PAIR].includes(candidate.pattern?.type),
  )
  if (immediate)
    return { best: immediate.index, modelBest: ranked[0].index, reason: 'FULLAUTO_WIN_NOW', qGap: 0 }

  const control = findControlPath(table, candidates)
  if (control >= 0)
    return {
      best: control,
      modelBest: ranked[0].index,
      reason: 'FULLAUTO_CONTROL_PATH',
      qGap: ranked[0].q - values[control],
    }

  let selected = ranked[0]
  if ([TYPE.BOMB, TYPE.ROCKET].includes(selected.pattern?.type) && selected.q < 0) {
    const nonBomb = ranked.find((candidate) => ![TYPE.BOMB, TYPE.ROCKET].includes(candidate.pattern?.type))
    if (nonBomb) selected = nonBomb
  }
  if (!selected.cards.length) {
    const response = ranked.find((candidate) => candidate.cards.length)
    if (response) {
      const responseScore = response.q * 8
      const scoreGap = (selected.q - response.q) * 8
      const landlordPressure =
        resolveFullAutoRole(table) === 'landlord' && responseScore > 0 && scoreGap < 0.2
      if (responseScore > 1 || landlordPressure)
        return {
          best: response.index,
          modelBest: ranked[0].index,
          reason: 'FULLAUTO_ACTIVE_RESPONSE',
          qGap: selected.q - response.q,
        }
    }
  }
  return {
    best: selected.index,
    modelBest: ranked[0].index,
    reason: selected.cards.length ? 'FULLAUTO_RESNET_MAX_Q' : 'FULLAUTO_STRATEGIC_PASS',
    qGap: ranked[0].q - selected.q,
  }
}

function candidateSummary(cards, q) {
  const pattern = classifyMove(cards)
  return {
    q,
    score: q == null ? null : q * 8,
    pass: cards.length === 0,
    type: pattern?.type || 0,
    cards: cards.map((card) => card.code),
    label: cards.length ? cards.map((card) => card.rank || card.code).join(' ') : '不出',
  }
}

export async function chooseFullAutoMove(table, onStatus) {
  const candidates = enumerateLegalMoves(table)
  if (!candidates.length) throw new Error('当前没有可提交的合法动作')
  if (candidates.length === 1)
    return {
      cards: candidates[0],
      candidates: 1,
      playableCandidates: candidates[0].length ? 1 : 0,
      q: null,
      role: resolveFullAutoRole(table),
      reason: candidates[0].length ? 'ONLY_LEGAL_MOVE' : 'RULE_FORCED_PASS',
      top: [candidateSummary(candidates[0], null)],
    }
  const role = resolveFullAutoRole(table)
  const stateQuality = analyzeStateQuality(table)
  if (!stateQuality.complete) {
    const index = selectHeuristicCandidate(table, candidates)
    return {
      cards: candidates[index],
      candidates: candidates.length,
      playableCandidates: candidates.filter((candidate) => candidate.length).length,
      q: null,
      role,
      reason: 'INCOMPLETE_STATE_HEURISTIC',
      fallback: true,
      stateQuality,
      top: [candidateSummary(candidates[index], null)],
    }
  }
  try {
    const ort = await ortRuntime()
    const session = await modelSession('play', role, onStatus)
    const values = await scorePlayCandidates(table, candidates, role, session, ort)
    if (values.length < candidates.length) throw new Error('FullAuto 返回的评分数量不完整')
    const selection = selectFullAutoCandidate(table, candidates, values)
    return {
      cards: candidates[selection.best],
      candidates: candidates.length,
      playableCandidates: candidates.filter((candidate) => candidate.length).length,
      q: values[selection.best],
      score: values[selection.best] * 8,
      modelQ: values[selection.modelBest],
      qGap: selection.qGap,
      role,
      modelFile: FULLAUTO_PLAY_MODELS[role],
      reason: selection.reason,
      fallback: false,
      stateQuality,
      top: values
        .map((q, index) => candidateSummary(candidates[index], q))
        .sort((left, right) => right.q - left.q)
        .slice(0, 8),
    }
  } catch (error) {
    onStatus?.('ERROR', 'FullAuto 模型暂不可用，使用保底策略')
    const index = selectHeuristicCandidate(table, candidates)
    return {
      cards: candidates[index],
      candidates: candidates.length,
      playableCandidates: candidates.filter((candidate) => candidate.length).length,
      q: null,
      role,
      reason: 'MODEL_FALLBACK',
      fallback: true,
      errorMessage: error instanceof Error ? error.message : String(error),
      stateQuality,
      top: [candidateSummary(candidates[index], null)],
    }
  }
}

export async function chooseFullAutoBid(table, actions, onStatus) {
  const available = new Set(actions || [])
  const robbing = available.has('ROB_LANDLORD') || available.has('PASS_ROB')
  const score = await scoreHand('bid', table.hand, onStatus)
  const previouslyActed = (table.actionHistory || []).some(
    (entry) => Number(entry.seatId) === Number(table.seatId) && /叫|抢/.test(entry.action),
  )
  const threshold = robbing ? (previouslyActed ? 0.4 : 0.5) : 0.2
  const accept = score > threshold
  return {
    accept,
    score,
    threshold,
    modelFile: PRE_GAME_MODELS.bid,
    label: accept ? (robbing ? '抢地主' : '叫地主') : robbing ? '不抢' : '不叫',
  }
}

function hasSuperPattern(hand) {
  const encoded = cardsToScoreArray(hand)
  const count = (column) => [0, 1, 2, 3].reduce((total, row) => total + encoded[row * 15 + column], 0)
  const ace = count(11)
  const two = count(12)
  const smallJoker = count(13)
  const bigJoker = count(14)
  return (
    (smallJoker && bigJoker) ||
    (bigJoker && two) ||
    (smallJoker && two >= 2) ||
    (two >= 3 && ace) ||
    (two >= 2 && ace >= 3)
  )
}

async function initialLandlordScore(table, onStatus) {
  const candidates = enumerateAllMoves(table.hand)
  const role = 'landlord'
  const ort = await ortRuntime()
  const session = await modelSession('play', role, onStatus)
  const values = await scorePlayCandidates(table, candidates, role, session, ort)
  return Math.max(...values) * 8
}

export async function chooseFullAutoDouble(table, actions, itemCount, onStatus) {
  const available = new Set(actions || [])
  const role = resolveFullAutoRole(table)
  const landlord = role === 'landlord'
  const modelFile = landlord
    ? FULLAUTO_PLAY_MODELS.landlord
    : PRE_GAME_MODELS[role === 'landlord_up' ? 'farmer_up' : 'farmer_down']
  const score = landlord
    ? await initialLandlordScore(table, onStatus)
    : await scoreHand(role === 'landlord_up' ? 'farmer_up' : 'farmer_down', table.hand, onStatus)
  const robbed = (table.actionHistory || []).filter((entry) => entry.action === '叫/抢地主').length > 1
  const superThreshold = landlord ? (robbed ? 0.6 : 0.5) : 1.2
  const doubleThreshold = landlord ? (robbed ? 0.5 : 0.4) : 1.0
  if (
    available.has('SUPER_DOUBLE') &&
    Number(itemCount) > 0 &&
    score > superThreshold &&
    hasSuperPattern(table.hand)
  )
    return {
      command: 'SUPER_MULTIPLE',
      payload: {},
      score,
      threshold: superThreshold,
      modelFile,
      label: '超级加倍',
    }
  const accept = score > doubleThreshold
  return {
    command: 'MULTIPLE',
    payload: { accept },
    score,
    threshold: doubleThreshold,
    modelFile,
    label: accept ? '普通加倍' : '不加倍',
  }
}

export function resetFullAutoSessions() {
  sessions.clear()
}
