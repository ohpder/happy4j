import { computed, reactive, ref } from 'vue'
import { authRequest, authToken, currentUser } from '@/services/api.js'
import { GameSocket } from '@/services/game-socket.js'
import { useToasts } from './use-toasts.js'
import { playerMessage } from '@/utils/player-message.js'

const state = reactive({
  user: null,
  connected: false,
  loading: true,
  modes: [],
  sessions: {},
  activeSessionId: null,
  provider: 'qq',
  preview: false,
  evicted: false,
  aiConfig: null,
  aiStopReason: '',
  records: { stats: {}, records: [] },
})

const screen = ref('lobby')
const lobbyTab = ref('modes')
const roomHidden = ref(false)
const qr = reactive({ open: false, loading: false, image: '', status: '请选择登录方式' })
let socket = null
const { push } = useToasts()

export function useGameClient() {
  const session = computed(() => state.sessions[state.activeSessionId] || null)
  const table = computed(() => session.value?.table || null)
  const authenticated = computed(() => state.preview || Boolean(state.user && authToken.get()))

  function updateSession(snapshot) {
    if (!snapshot?.id) return
    state.sessions[snapshot.id] = snapshot
    const current = state.sessions[state.activeSessionId]
    if (!state.activeSessionId || ['DISCONNECTED', 'FAILED'].includes(current?.phase))
      state.activeSessionId = snapshot.id
    const active = state.sessions[state.activeSessionId]
    if (active?.phase === 'EVICTED') {
      roomHidden.value = false
      state.evicted = true
      screen.value = 'lobby'
      return
    }
    // A socket failure may arrive before the table snapshot is cleared (or a
    // reconnect can restore the phase while an old snapshot is still cached).
    // Connection phases always belong in the lobby so the room view cannot be
    // left with disabled actions and no usable game connection.
    if (['CREATED', 'HALL_CONNECTED', 'DISCONNECTED', 'FAILED'].includes(active?.phase)) {
      roomHidden.value = false
      screen.value = 'lobby'
      return
    }
    if (roomHidden.value) return
    if (active?.table?.sceneId > 0 && active.table.stage !== 'IDLE') screen.value = 'room'
    if (!active?.table?.sceneId || active.table.stage === 'IDLE') screen.value = 'lobby'
  }

  function hideRoom() {
    roomHidden.value = true
    screen.value = 'lobby'
  }

  function showRoom() {
    roomHidden.value = false
    const active = state.sessions[state.activeSessionId]
    if (active?.table?.sceneId > 0 && active.table.stage !== 'IDLE') screen.value = 'room'
    else screen.value = 'lobby'
  }

  async function initialize() {
    const preview = import.meta.env.DEV ? new URLSearchParams(location.search).get('preview') : null
    if (preview) {
      const { previewState } = await import('@/dev/preview-data.js')
      const data = previewState(preview)
      state.preview = true
      state.user = data.user
      state.connected = true
      state.modes = data.modes
      state.sessions = { [data.session.id]: data.session }
      state.activeSessionId = data.session.id
      state.evicted = preview === 'evicted'
      state.aiConfig = data.aiConfig || null
      state.records = { stats: {}, records: [] }
      state.aiStopReason = ''
      roomHidden.value = false
      screen.value = ['lobby', 'evicted', 'ai-config'].includes(preview) ? 'lobby' : 'room'
      state.loading = false
      return
    }
    state.user = await currentUser()
    state.loading = false
    if (state.user) connect()
  }

  async function authenticate(path, values) {
    const result = await authRequest(path, values)
    authToken.set(result.token)
    state.user = result.user
    connect()
  }

  function connect() {
    socket?.close()
    socket = new GameSocket(handleEnvelope, (connected) => {
      state.connected = connected
    })
    socket.connect()
  }

  async function handleEnvelope(envelope) {
    if (envelope.type === 'RESULT' && envelope.payload?.command === 'BOOT') {
      const data = envelope.payload.data
      state.user = data.user
      state.modes = data.modes || []
      state.aiConfig = data.aiConfig || null
      state.aiStopReason = ''
      state.sessions = Object.fromEntries((data.sessions || []).map((item) => [item.id, item]))
      state.activeSessionId = data.sessions?.[0]?.id || null
      if (!state.activeSessionId) {
        try {
          updateSession(await socket.command('SESSION_RESUME'))
        } catch {
          try {
            updateSession(await socket.command('SESSION_CREATE'))
          } catch (error) {
            push(playerMessage(error.message), 'error')
          }
        }
      } else updateSession(state.sessions[state.activeSessionId])
      try {
        state.records = await socket.command('RECORDS_GET', { limit: 30 })
      } catch {
        // History is supplementary; the lobby remains usable when it is unavailable.
      }
      return
    }
    if (envelope.type === 'SNAPSHOT') updateSession(envelope.payload)
    if (
      envelope.type === 'EVENT' &&
      envelope.payload?.kind === 'GAME_SO' &&
      /subtype=(5|102)\b/.test(envelope.payload.message || '')
    ) {
      setTimeout(() => {
        socket
          ?.command('RECORDS_GET', { limit: 30 })
          .then((result) => {
            state.records = result || { stats: {}, records: [] }
          })
          .catch(() => {})
      }, 500)
    }
    if (
      envelope.type === 'EVENT' &&
      envelope.payload?.kind === 'AI_SERVER' &&
      envelope.payload.message?.includes('已停止')
    ) {
      state.aiStopReason = envelope.payload.message
      if (state.aiConfig) state.aiConfig = { ...state.aiConfig, enabled: false, stopAfterRound: false }
    }
    if (envelope.type === 'QR_STATUS') {
      qr.status = playerMessage(envelope.payload.message)
      if (envelope.payload.status === 'CONFIRMED') {
        push('游戏账号登录成功')
        setTimeout(() => (qr.open = false), 500)
      }
    }
    if (envelope.type === 'ERROR' && !envelope.requestId)
      push(playerMessage(envelope.payload.message), 'error')
  }

  async function command(name, extra = {}, successMessage = '') {
    const userCommands = new Set([
      'SESSION_CREATE',
      'SESSION_RESUME',
      'AI_CONFIG_GET',
      'AI_CONFIG_SAVE',
      'AI_STOP',
      'AI_PROVIDER_TEST',
      'RECORDS_GET',
    ])
    if (!state.activeSessionId && !userCommands.has(name)) {
      throw new Error('请先建立游戏会话')
    }
    try {
      const payload = userCommands.has(name) ? extra : { sessionId: state.activeSessionId, ...extra }
      const result = await socket.command(name, payload)
      if (result?.id && result?.phase) updateSession(result)
      if (name === 'AI_CONFIG_GET' || name === 'AI_CONFIG_SAVE' || name === 'AI_STOP') state.aiConfig = result
      if (name === 'RECORDS_GET') state.records = result || { stats: {}, records: [] }
      if (successMessage) push(successMessage)
      return result
    } catch (error) {
      const message = playerMessage(error.message)
      push(message, 'error')
      throw new Error(message, { cause: error })
    }
  }

  async function startQr(provider) {
    qr.loading = true
    qr.image = ''
    qr.status = `正在生成${provider === 'wechat' ? '微信' : 'QQ'}二维码`
    state.provider = provider
    try {
      const result = await command('QR_START', { provider })
      qr.image = result.image
      qr.status = `请使用${provider === 'wechat' ? '微信' : 'QQ'}扫码并确认登录`
    } finally {
      qr.loading = false
    }
  }

  function openQr() {
    qr.open = true
  }

  function logout() {
    socket?.close()
    socket = null
    authToken.clear()
    state.user = null
    state.sessions = {}
    state.activeSessionId = null
    state.aiConfig = null
    state.aiStopReason = ''
    state.records = { stats: {}, records: [] }
    roomHidden.value = false
    screen.value = 'lobby'
  }

  return {
    state,
    screen,
    lobbyTab,
    roomHidden,
    qr,
    session,
    table,
    authenticated,
    initialize,
    authenticate,
    command,
    startQr,
    openQr,
    logout,
    hideRoom,
    showRoom,
    updateSession,
  }
}
