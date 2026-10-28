<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import CardCounter from '@/components/CardCounter.vue'
import AiRoomLogPanel from '@/components/AiRoomLogPanel.vue'
import PlayingCard from '@/components/PlayingCard.vue'
import PlayerSeat from '@/components/PlayerSeat.vue'
import SettlementDialog from '@/components/SettlementDialog.vue'
import TurnTimer from '@/components/TurnTimer.vue'
import TableTimeoutDialog from '@/components/TableTimeoutDialog.vue'
import defaultAvatar from '@/assets/original/default-avatar.png'
import { buildSuggestions, hasBeatingPlay, remainingCardCounts, sortCards } from '@/utils/cards.js'
import { playerMessage } from '@/utils/player-message.js'
import {
  isRoundResultSnapshot,
  isTableTimeoutSnapshot,
  rightClickAction,
  shouldTrustOnTimeout,
  trustControlAction,
} from '@/utils/room-state.js'
import { itemCount, SUPER_DOUBLE_ITEM_ID } from '@/utils/inventory.js'

const props = defineProps({
  session: { type: Object, required: true },
  execute: { type: Function, required: true },
  aiRuntime: Object,
  clearAiLogs: Function,
})
const emit = defineEmits(['lobby', 'ai-stop'])
const selected = ref(new Set())
const counterOpen = ref(false)
const settlementVisible = ref(false)
const seconds = ref(0)
const drag = reactive({ active: false, selecting: true })
const hintIndex = ref(0)
const viewportWidth = ref(window.innerWidth)
const actionPending = ref(false)
const timeoutTrustKey = ref('')
const roomLogOpen = ref(true)
let timer = null

watch(
  () => props.aiRuntime?.active,
  (active, previous) => {
    if (active && !previous) roomLogOpen.value = true
  },
)

const table = computed(() => props.session.table)
const serverHosting = computed(() => props.aiRuntime?.hostingMode === 'SERVER')
const hand = computed(() => sortCards(table.value?.hand || []))
const localPlayer = computed(
  () => table.value?.players?.find((player) => player.seatId === table.value.seatId) || null,
)
const playerAtOffset = (offset) => {
  const local = table.value?.seatId ?? 0
  const seat = (local + offset) % 3
  return table.value?.players?.find((player) => player.seatId === seat) || { seatId: seat, cardCount: 0 }
}
const rightPlayer = computed(() => playerAtOffset(1))
const leftPlayer = computed(() => playerAtOffset(2))
const counts = computed(() => remainingCardCounts(table.value))
const isMyTurn = computed(() => table.value?.turnSeatId === table.value?.seatId)
const canPlay = computed(() => table.value?.availableActions?.includes('PLAY'))
const actionSet = computed(() => new Set(table.value?.availableActions || []))
const superDoubleItemId = computed(() => table.value?.superDoubleItemId || SUPER_DOUBLE_ITEM_ID)
const superDoubleCount = computed(() => itemCount(props.session, superDoubleItemId.value))
const canBeatLastPlay = computed(() =>
  hasBeatingPlay(
    hand.value,
    table.value?.lastPlayType,
    table.value?.lastPlayRank,
    table.value?.lastPlay?.length || 0,
  ),
)
const forcedPass = computed(
  () => isMyTurn.value && canPlay.value && actionSet.value.has('PASS') && !canBeatLastPlay.value,
)
const turnDuration = computed(() => {
  if ((table.value?.turnSeatId ?? -1) < 0) return 0
  if (forcedPass.value) return 10
  const serverSeconds = Number(table.value?.operationSeconds || 0)
  if (serverSeconds > 0) return serverSeconds
  if (table.value?.stage === 'CALLING_LANDLORD') return 15
  if (table.value?.stage === 'DOUBLING') return 6
  if (table.value?.stage === 'PLAYING') return table.value?.lastPlay?.length ? 25 : 30
  return 0
})
const location = computed(() => `场次 ${table.value?.sceneId || '—'} · 桌号 ${table.value?.tableId || '—'}`)
const roomPrompt = computed(() => playerMessage(table.value?.prompt || '等待牌局开始'))
const hasRoundResult = computed(() => isRoundResultSnapshot(table.value))
const trustAction = computed(() => trustControlAction(table.value))
const tableTimedOut = computed(() => isTableTimeoutSnapshot(table.value))
const handOverlap = computed(() => {
  if (hand.value.length <= 1) return 0
  const mobile = viewportWidth.value <= 720
  const cardWidth = mobile ? 54 : Math.min(78, viewportWidth.value * 0.052)
  const available = mobile ? viewportWidth.value - 12 : viewportWidth.value - 340
  const gap = (available - cardWidth) / (hand.value.length - 1)
  return Math.max(-cardWidth * 0.82, Math.min(-12, gap - cardWidth))
})
const formatNumber = (value) => new Intl.NumberFormat('zh-CN').format(Number(value || 0))

const actionLabels = {
  READY: '准备',
  CALL_LANDLORD: '叫地主',
  PASS_LANDLORD: '不叫',
  ROB_LANDLORD: '抢地主',
  PASS_ROB: '不抢',
  DOUBLE: '加倍',
  SUPER_DOUBLE: '超级加倍',
  NO_DOUBLE: '不加倍',
  PLAY: '出牌',
  PASS: '不出',
  TRUST: '托管',
  CANCEL_TRUST: '取消托管',
  CHANGE_TABLE: '换桌',
  LEAVE_ROOM: '退出',
}

const primaryActions = computed(() => {
  const actions = (table.value?.availableActions || []).filter(
    (action) => !['TRUST', 'CANCEL_TRUST', 'CHANGE_TABLE', 'LEAVE_ROOM'].includes(action),
  )
  return forcedPass.value ? actions.filter((action) => action === 'PASS') : actions
})

function updateSelection(code, selecting) {
  const next = new Set(selected.value)
  if (selecting) next.add(code)
  else next.delete(code)
  selected.value = next
}

function beginDrag(code) {
  drag.active = true
  drag.selecting = !selected.value.has(code)
  updateSelection(code, drag.selecting)
}

function enterDrag(code) {
  if (drag.active) updateSelection(code, drag.selecting)
}

function endDrag() {
  drag.active = false
}

function moveDrag(event) {
  if (!drag.active) return
  const element = document.elementFromPoint(event.clientX, event.clientY)?.closest('[data-card-code]')
  if (element) updateSelection(Number(element.dataset.cardCode), drag.selecting)
}

function updateViewport() {
  viewportWidth.value = window.innerWidth
}

function avatarFallback(event) {
  if (event.currentTarget.src !== defaultAvatar) event.currentTarget.src = defaultAvatar
}

function hint() {
  const suggestions = buildSuggestions(
    hand.value,
    table.value.lastPlayType,
    table.value.lastPlayRank,
    table.value.lastPlay?.length || 0,
  )
  if (!suggestions.length) {
    selected.value = new Set()
    return
  }
  const cards = suggestions[hintIndex.value++ % suggestions.length]
  selected.value = new Set(cards.map((card) => card.code))
}

function payloadFor(action) {
  if (action === 'CALL_LANDLORD' || action === 'ROB_LANDLORD') return ['ORDER', { accept: true }]
  if (action === 'PASS_LANDLORD' || action === 'PASS_ROB') return ['ORDER', { accept: false }]
  if (action === 'DOUBLE') return ['MULTIPLE', { accept: true }]
  if (action === 'SUPER_DOUBLE') return ['SUPER_MULTIPLE', {}]
  if (action === 'NO_DOUBLE') return ['MULTIPLE', { accept: false }]
  if (action === 'TRUST') return ['TRUST', { enabled: true }]
  if (action === 'CANCEL_TRUST') return ['TRUST', { enabled: false }]
  if (action === 'PLAY') return ['PLAY', { pass: false, cards: [...selected.value] }]
  if (action === 'PASS') return ['PLAY', { pass: true, cards: [] }]
  return [action, {}]
}

function actionDisabled(action) {
  if (props.aiRuntime?.active) return true
  if (actionPending.value) return true
  if (action === 'PLAY') return selected.value.size === 0
  if (action === 'SUPER_DOUBLE') return superDoubleCount.value <= 0
  return false
}

function exitRoom() {
  if (props.aiRuntime?.active) {
    emit('lobby')
    return
  }
  void act('LEAVE_ROOM')
}

async function act(action) {
  if (actionPending.value || props.aiRuntime?.active) return
  if (action === 'PLAY' && selected.value.size === 0) return
  const [command, payload] = payloadFor(action)
  actionPending.value = true
  try {
    const result = await props.execute(command, payload)
    if (result === null) return
    if (command === 'PLAY') selected.value = new Set()
    if (command === 'LEAVE_ROOM') emit('lobby')
  } finally {
    actionPending.value = false
  }
}

function rightClickPlay() {
  if (props.aiRuntime?.active) return
  const action = rightClickAction(table.value, selected.value.size, forcedPass.value)
  if (action) void act(action)
}

function trustOnTimeoutOnce() {
  if (props.aiRuntime?.active) return
  if (!shouldTrustOnTimeout(table.value, seconds.value, isMyTurn.value)) return
  const key = `${table.value?.turnSeatId}|${table.value?.actionHistory?.length || 0}`
  if (timeoutTrustKey.value === key) return
  timeoutTrustKey.value = key
  void act('TRUST')
}

watch(
  () => (table.value?.hand || []).map((card) => card.code).join(','),
  () => {
    const available = new Set((table.value?.hand || []).map((card) => card.code))
    selected.value = new Set([...selected.value].filter((code) => available.has(code)))
  },
)

watch(
  () =>
    `${table.value?.stage}|${table.value?.turnSeatId}|${table.value?.availableActions?.join(',')}|${table.value?.actionHistory?.length}|${turnDuration.value}`,
  () => {
    seconds.value = turnDuration.value
    hintIndex.value = 0
    timeoutTrustKey.value = ''
  },
  { immediate: true },
)

watch(
  () => `${table.value?.stage}|${hasRoundResult.value}|${table.value?.actionHistory?.length || 0}`,
  () => {
    settlementVisible.value = table.value?.stage === 'SETTLED' && hasRoundResult.value
  },
  { immediate: true },
)

onMounted(() => {
  window.addEventListener('pointerup', endDrag)
  window.addEventListener('pointermove', moveDrag)
  window.addEventListener('resize', updateViewport)
  timer = setInterval(() => {
    if ((table.value?.turnSeatId ?? -1) >= 0 && seconds.value > 0) seconds.value--
    trustOnTimeoutOnce()
  }, 1000)
})
onBeforeUnmount(() => {
  window.removeEventListener('pointerup', endDrag)
  window.removeEventListener('pointermove', moveDrag)
  window.removeEventListener('resize', updateViewport)
  clearInterval(timer)
})
</script>

<template>
  <main
    class="room-page"
    :class="{ 'ai-controlled': aiRuntime?.active, 'server-spectator': serverHosting }"
    @contextmenu.stop.prevent="rightClickPlay"
  >
    <div class="table-texture"></div>
    <div class="table-glow"></div>
    <div class="table-monogram" aria-hidden="true"><b>HL</b><span>欢乐竞技牌桌</span></div>
    <header class="room-header">
      <div class="room-header-left">
        <button
          class="room-back"
          type="button"
          :disabled="!props.aiRuntime?.active && !actionSet.has('LEAVE_ROOM')"
          @click="exitRoom"
        >
          <span>‹</span>{{ props.aiRuntime?.active ? '回到大厅' : '退出' }}
        </button>
        <div class="room-brand">
          <i>♠</i><span><b>欢乐牌局</b><small>PREMIUM TABLE</small></span>
        </div>
      </div>
      <div class="room-location">
        <strong>{{ location }}</strong
        ><small><i></i>经典斗地主 · 公平竞技中</small>
      </div>
      <div class="room-stats">
        <button
          v-if="aiRuntime?.active"
          class="ai-room-log"
          :class="{ active: roomLogOpen }"
          type="button"
          @click="roomLogOpen = !roomLogOpen"
        >
          日志 <b>{{ aiRuntime.logs?.length || 0 }}</b>
        </button>
        <button v-if="aiRuntime?.active" class="ai-room-control" type="button" @click="emit('ai-stop')">
          <span><i></i>{{ serverHosting ? '服务器托管 · 观战中' : 'AI 托管中' }}</span
          ><small>{{
            aiRuntime.stopAfterRound
              ? '本局结束后停止托管'
              : serverHosting
                ? '浏览器实时接收牌桌快照'
                : aiRuntime.status
          }}</small
          ><b>{{ aiRuntime.stopAfterRound ? '已排队' : '停止' }}</b>
        </button>
        <span class="multiplier-panel"
          ><small>底牌倍数</small><b>×{{ table.multiplier || 1 }}</b></span
        >
        <button type="button" :class="{ active: counterOpen }" @click="counterOpen = !counterOpen">
          <i>◫</i>记牌器
        </button>
      </div>
    </header>

    <PlayerSeat
      :player="leftPlayer"
      :landlord="leftPlayer.seatId === table.landlordSeatId"
      :active="leftPlayer.seatId === table.turnSeatId"
      :seconds="seconds"
      :duration="turnDuration"
      side="left"
    />
    <PlayerSeat
      :player="rightPlayer"
      :landlord="rightPlayer.seatId === table.landlordSeatId"
      :active="rightPlayer.seatId === table.turnSeatId"
      :seconds="seconds"
      :duration="turnDuration"
      side="right"
    />

    <section class="landlord-cards" :class="{ revealed: table.bottomCards?.length }">
      <span>地主牌</span>
      <div>
        <template v-if="table.bottomCards?.length">
          <PlayingCard
            v-for="(card, index) in sortCards(table.bottomCards)"
            :key="card.code"
            :card="card"
            :index="index"
            :count="3"
            compact
          />
        </template>
        <template v-else><i v-for="index in 3" :key="index">?</i></template>
      </div>
    </section>

    <section class="table-center">
      <div class="center-spotlight"></div>
      <div v-if="table.lastPlay?.length" class="last-play-stack">
        <span
          >{{
            table.players?.find((player) => player.seatId === table.lastPlaySeatId)?.nickname || '玩家'
          }}
          出牌</span
        >
        <div>
          <PlayingCard
            v-for="(card, index) in sortCards(table.lastPlay)"
            :key="card.code"
            :card="card"
            :index="index"
            :count="table.lastPlay.length"
            compact
          />
        </div>
      </div>
      <div v-else class="round-prompt">
        <span class="prompt-suits">♠ <i>♥</i> ♣ <i>♦</i></span>
        <strong>{{ roomPrompt }}</strong>
      </div>
    </section>

    <section class="room-controls">
      <div class="action-prompt" :class="{ active: isMyTurn }">
        <TurnTimer :seconds="seconds" :duration="turnDuration" :active="isMyTurn" />
        <span>{{ roomPrompt }}</span>
      </div>
      <div class="action-buttons">
        <button v-if="canPlay && !forcedPass" class="game-button hint" type="button" @click="hint">
          智能选牌
        </button>
        <button
          v-for="action in primaryActions"
          :key="action"
          class="game-button"
          :class="[
            ['PLAY', 'CALL_LANDLORD', 'ROB_LANDLORD', 'DOUBLE', 'SUPER_DOUBLE', 'READY'].includes(action)
              ? 'gold'
              : 'soft',
            {
              'item-action': action === 'SUPER_DOUBLE',
              unavailable: action === 'SUPER_DOUBLE' && superDoubleCount <= 0,
            },
          ]"
          type="button"
          :disabled="actionDisabled(action)"
          @click="act(action)"
        >
          <span>{{ actionLabels[action] }}</span>
          <small v-if="action === 'SUPER_DOUBLE'">卡 ×{{ formatNumber(superDoubleCount) }}</small>
        </button>
      </div>
    </section>

    <section class="self-area">
      <div class="self-profile">
        <img :src="session.avatarUrl || defaultAvatar" alt="我的头像" @error="avatarFallback" />
        <div>
          <strong>{{ session.nickname || localPlayer?.nickname || '我' }}</strong
          ><span>{{ localPlayer?.cardCount ?? hand.length }} 张手牌</span
          ><small><i>豆</i>{{ formatNumber(localPlayer?.beans || session.beans || 0) }}</small>
        </div>
        <b v-if="table.seatId === table.landlordSeatId">地主</b><b v-else>农民</b>
      </div>
      <div class="hand-zone" :class="{ dragging: drag.active }">
        <PlayingCard
          v-for="(card, index) in hand"
          :key="card.code"
          :card="card"
          :selected="selected.has(card.code)"
          :index="index"
          :count="hand.length"
          :overlap="handOverlap"
          @drag-start="beginDrag"
          @drag-enter="enterDrag"
        />
      </div>
      <div class="secondary-actions">
        <button
          v-if="trustAction"
          :class="{ active: trustAction === 'CANCEL_TRUST' }"
          type="button"
          :aria-disabled="aiRuntime?.active"
          :disabled="actionPending || aiRuntime?.active"
          @click="act(trustAction)"
        >
          {{ trustAction === 'CANCEL_TRUST' ? '取消托管' : '托管' }}
        </button>
      </div>
    </section>

    <CardCounter :counts="counts" :open="counterOpen" @close="counterOpen = false" />
    <AiRoomLogPanel
      v-if="aiRuntime?.active"
      :open="roomLogOpen"
      :runtime="aiRuntime"
      @toggle="roomLogOpen = !roomLogOpen"
      @clear="clearAiLogs?.()"
    />
    <SettlementDialog
      v-if="settlementVisible"
      :table="table"
      :beans="session.beans"
      @close="settlementVisible = false"
      @action="act"
    />
    <TableTimeoutDialog v-if="tableTimedOut" :table="table" @action="act" />
  </main>
</template>
