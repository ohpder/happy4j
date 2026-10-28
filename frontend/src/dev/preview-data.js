function card(code) {
  if (code === 53) return { code, rank: '小王', suit: '', selected: false }
  if (code === 54) return { code, rank: '大王', suit: '', selected: false }
  const rank = ((code - 1) % 13) + 1
  const suit = Math.floor((code - 1) / 13)
  const names = { 1: 'A', 2: '2', 11: 'J', 12: 'Q', 13: 'K' }
  return {
    code,
    rank: names[rank] || String(rank),
    suit: ['♠', '♥', '♣', '♦'][suit],
    selected: false,
  }
}

const handCodes = [54, 53, 2, 1, 13, 26, 12, 25, 11, 24, 10, 23, 9, 22, 8, 21, 7, 20, 6, 19]

export function previewState(kind) {
  const settled = kind === 'settlement'
  const staleSettlement = kind === 'stale-settlement'
  const doubling = kind === 'doubling' || kind === 'no-super-double'
  const forcedPass = kind === 'pass'
  const opponentLandlord = kind === 'opponent'
  const opponentTurn = kind === 'opponent-turn'
  const timedOut = kind === 'timeout'
  const trusted = kind === 'trusted'
  const visibleHandCodes = forcedPass ? [3, 16, 4, 17, 5, 18] : handCodes
  const table = {
    sceneId: 2260,
    roomId: 0,
    tableId: 8062,
    seatId: 0,
    turnSeatId: settled || staleSettlement || timedOut ? -1 : opponentTurn ? 1 : 0,
    landlordSeatId: opponentLandlord ? 1 : 0,
    hand: settled ? handCodes.slice(0, 8).map(card) : visibleHandCodes.map(card),
    lastPlay: forcedPass ? [card(10), card(23)] : [card(5), card(18)],
    lastPlayType: 3,
    lastPlayRank: forcedPass ? 8 : 3,
    lastPlaySeatId: 2,
    bottomCards: [card(54), card(1), card(14)],
    players: [
      {
        uin: 397911882,
        nickname: '欢乐玩家',
        seatId: 0,
        cardCount: settled ? 8 : 20,
        beans: 5790,
        ready: false,
        avatarUrl: '',
      },
      {
        uin: 10002,
        nickname: '夏日橘子',
        seatId: 1,
        cardCount: settled ? 0 : 16,
        beans: 28600,
        ready: false,
        avatarUrl: '',
      },
      {
        uin: 10003,
        nickname: '一只小黄鸡',
        seatId: 2,
        cardCount: settled ? 5 : 15,
        beans: 12680,
        ready: false,
        avatarUrl: '',
      },
    ],
    actionHistory: [
      {
        occurredAt: new Date().toISOString(),
        seatId: 2,
        player: '一只小黄鸡',
        action: '出牌',
        cards: [card(5), card(18)],
      },
      { occurredAt: new Date().toISOString(), seatId: 1, player: '夏日橘子', action: '不出', cards: [] },
    ],
    stage:
      settled || staleSettlement ? 'SETTLED' : timedOut ? 'TIMED_OUT' : doubling ? 'DOUBLING' : 'PLAYING',
    availableActions: timedOut
      ? ['CHANGE_TABLE', 'LEAVE_ROOM']
      : settled || staleSettlement
        ? ['READY', 'CHANGE_TABLE', 'LEAVE_ROOM']
        : doubling
          ? ['DOUBLE', 'SUPER_DOUBLE', 'NO_DOUBLE']
          : trusted
            ? ['CANCEL_TRUST']
            : opponentTurn
              ? []
              : ['PLAY', 'PASS', 'TRUST'],
    multiplier: 30,
    superDoubleItemId: 30020001,
    operationSeconds: 25,
    trusted,
    settlement: settled
      ? {
          resultFlag: 1,
          players: [
            {
              seatId: 0,
              beanChange: -1200,
              beans: 4590,
              factor: 30,
              remainingCards: handCodes.slice(0, 8).map(card),
            },
            { seatId: 1, beanChange: 2400, beans: 31000, factor: 15, remainingCards: [] },
            {
              seatId: 2,
              beanChange: -1200,
              beans: 11480,
              factor: 15,
              remainingCards: [5, 18, 31, 44, 7].map(card),
            },
          ],
        }
      : { resultFlag: 0, players: [] },
    prompt: timedOut
      ? '本桌等待超时，请换桌继续'
      : settled
        ? '本局结束：可再来一局、换桌或退出房间'
        : staleSettlement
          ? '已返回牌桌，等待下一步操作'
          : doubling
            ? '请选择是否加倍'
            : '轮到你出牌',
  }
  const session = {
    id: 'preview-session',
    phase: 'IN_ROOM',
    detail: '牌局进行中',
    uin: 397911882,
    nickname: '欢乐玩家',
    avatarUrl: '',
    beans: 5790,
    diamonds: 18,
    walletRefreshedAt: new Date().toISOString(),
    activities: [
      { id: 'sign-in', title: '每日签到', status: 'READY' },
      { id: 'competition', title: '欢乐赛事', status: 'READY' },
      { id: 'bean-discount', title: '欢乐豆优惠', status: 'READY' },
    ],
    missions: [
      { missionId: 1563, title: '每日欢乐豆补助', status: 2, periodFinishCount: 2, claimable: false },
      { missionId: 2666, title: '完成不洗牌玩法对局', status: 1, periodFinishCount: 3, claimable: false },
      { missionId: 2822, title: '担任地主并获胜', status: 3, periodFinishCount: 1, claimable: true },
    ],
    inventory: [
      {
        itemId: 30020001,
        name: '超级加倍卡',
        count: kind === 'no-super-double' ? 0 : 3,
        refreshedAt: new Date().toISOString(),
      },
    ],
    table: ['lobby', 'evicted', 'ai-config'].includes(kind) ? { ...table, sceneId: 0, stage: 'IDLE' } : table,
  }
  return {
    user: { username: 'preview', displayName: '欢乐玩家' },
    session,
    aiConfig: {
      sceneId: 15260,
      hostingMode: kind === 'ai-config' ? 'SERVER' : 'BROWSER',
      autoChangeTable: true,
      autoClaimBeans: true,
      stopAtBeans: 100000,
      stopBelowBeans: 1000,
      inferenceEngine: kind === 'ai-config' ? 'OPENAI' : 'DOUZERO',
      apiBaseUrl: kind === 'ai-config' ? 'https://api.openai.com/v1' : '',
      apiModel: kind === 'ai-config' ? 'gpt-5.4' : '',
      apiKeyConfigured: kind === 'ai-config',
      enabled: kind === 'ai-room',
      updatedAt: new Date().toISOString(),
    },
    modes: [
      {
        sceneId: 1260,
        family: 'CLASSIC',
        name: '新手场',
        minBeans: 1000,
        maxBeans: 80000,
        cap: 3000,
        baseScore: 5,
        enabled: true,
      },
      {
        sceneId: 15260,
        family: 'CLASSIC',
        name: '初级场',
        minBeans: 2000,
        maxBeans: 300000,
        cap: 20000,
        baseScore: 10,
        enabled: true,
      },
      {
        sceneId: 2260,
        family: 'CLASSIC',
        name: '中级场',
        minBeans: 10000,
        maxBeans: 0,
        cap: 30000,
        baseScore: 20,
        enabled: true,
      },
      {
        sceneId: 4260,
        family: 'LAIZI',
        name: '癞子新手场',
        minBeans: 1000,
        maxBeans: 80000,
        cap: 5000,
        baseScore: 8,
        enabled: true,
      },
      {
        sceneId: 5260,
        family: 'LAIZI',
        name: '癞子中级场',
        minBeans: 10000,
        maxBeans: 0,
        cap: 60000,
        baseScore: 25,
        enabled: true,
      },
      {
        sceneId: 9260,
        family: 'CHALLENGE',
        name: '新手挑战赛',
        minBeans: 1000,
        maxBeans: 300000,
        cap: 0,
        baseScore: 10,
        enabled: true,
      },
    ],
  }
}
