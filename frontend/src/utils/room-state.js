export function isRoundResultSnapshot(table) {
  if (table?.stage !== 'SETTLED') return false
  return (
    table?.settlement?.players?.length === 3 ||
    table?.actionHistory?.some((entry) => entry.action === '本局结束')
  )
}

export function trustControlAction(table) {
  if (table?.stage !== 'PLAYING') return null
  return table?.trusted ? 'CANCEL_TRUST' : 'TRUST'
}

export function isTableTimeoutSnapshot(table) {
  return table?.stage === 'TIMED_OUT'
}

export function rightClickAction(table, selectedCount, forcedPass) {
  const actions = new Set(table?.availableActions || [])
  if (forcedPass && actions.has('PASS')) return 'PASS'
  if (Number(selectedCount) > 0 && actions.has('PLAY')) return 'PLAY'
  return null
}

export function shouldTrustOnTimeout(table, seconds, isMyTurn) {
  return (
    Number(seconds) === 0 &&
    table?.stage === 'PLAYING' &&
    Boolean(isMyTurn) &&
    !table?.trusted &&
    table?.availableActions?.includes('TRUST')
  )
}
