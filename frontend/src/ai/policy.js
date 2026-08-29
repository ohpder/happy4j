import { evaluateHandStrength } from './douzero.js'

export const HIGH_WIN_THRESHOLDS = Object.freeze({
  callLandlord: 12,
  robLandlord: 18,
  double: 16,
  superDouble: 24,
})

export function chooseLandlordDecision(actions, hand) {
  const available = new Set(actions || [])
  const robbing = available.has('ROB_LANDLORD') || available.has('PASS_ROB')
  const strength = evaluateHandStrength(hand)
  const accept = strength >= (robbing ? HIGH_WIN_THRESHOLDS.robLandlord : HIGH_WIN_THRESHOLDS.callLandlord)
  return {
    accept,
    strength,
    label: accept ? (robbing ? '抢地主' : '叫地主') : robbing ? '不抢' : '不叫',
  }
}

export function chooseDoubleDecision(actions, hand, superDoubleItemCount) {
  const available = new Set(actions || [])
  const strength = evaluateHandStrength(hand)
  if (
    available.has('SUPER_DOUBLE') &&
    Number(superDoubleItemCount) > 0 &&
    strength >= HIGH_WIN_THRESHOLDS.superDouble
  )
    return { command: 'SUPER_MULTIPLE', payload: {}, strength, label: '超级加倍' }
  const accept = strength >= HIGH_WIN_THRESHOLDS.double
  return {
    command: 'MULTIPLE',
    payload: { accept },
    strength,
    label: accept ? '普通加倍' : '不加倍',
  }
}
