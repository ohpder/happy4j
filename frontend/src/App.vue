<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import AiLogDrawer from '@/components/AiLogDrawer.vue'
import AiStopDialog from '@/components/AiStopDialog.vue'
import AuthScreen from '@/components/AuthScreen.vue'
import QrModal from '@/components/QrModal.vue'
import ToastStack from '@/components/ToastStack.vue'
import SessionEvictedModal from '@/components/SessionEvictedModal.vue'
import LobbyView from '@/views/LobbyView.vue'
import RoomView from '@/views/RoomView.vue'
import { useGameClient } from '@/composables/use-game-client.js'
import { useGameAi } from '@/composables/use-game-ai.js'
import { useToasts } from '@/composables/use-toasts.js'
import { playerMessage } from '@/utils/player-message.js'

const client = useGameClient()
const ai = useGameAi(client)
const { push } = useToasts()
const aiLogOpen = ref(false)
const aiStopOpen = ref(false)
const aiStopPending = ref(false)

async function authenticate(path, values) {
  try {
    await client.authenticate(path, values)
  } catch (error) {
    push(playerMessage(error.message), 'error')
  }
}

async function enterMode(mode) {
  try {
    client.showRoom()
    await client.command('MODE_ENTER', { sceneId: Number(mode.sceneId) })
    client.showRoom()
  } catch {
    client.hideRoom()
  }
}

async function execute(command, payload = {}, successMessage = '') {
  try {
    return await client.command(command, payload, successMessage)
  } catch {
    return null
  }
}

async function startAi(values) {
  try {
    await ai.start(values)
    push('AI 托管已启动')
  } catch (error) {
    push(playerMessage(error.message), 'error')
  }
}

function openStopDialog() {
  aiStopOpen.value = true
}

async function stopAi(mode) {
  aiStopPending.value = true
  try {
    const result = await ai.stop(mode)
    aiStopOpen.value = false
    push(mode === 'AFTER_ROUND' && result?.enabled ? '已设置本局结束后停止托管' : 'AI 托管已停止')
  } catch (error) {
    push(playerMessage(error.message), 'error')
  } finally {
    aiStopPending.value = false
  }
}

async function testAiProvider(values) {
  try {
    const result = await ai.testProvider(values)
    push(`大模型连接成功 · ${result.model} · ${result.latencyMs}ms`)
  } catch (error) {
    push(playerMessage(error.message), 'error')
  }
}

function logout() {
  client.logout()
}

function relogin() {
  client.state.evicted = false
  client.openQr()
}

function blockContextMenu(event) {
  event.preventDefault()
}

onMounted(() => {
  document.addEventListener('contextmenu', blockContextMenu)
  client.initialize()
})
onBeforeUnmount(() => document.removeEventListener('contextmenu', blockContextMenu))
</script>

<template>
  <div v-if="client.state.loading" class="boot-screen">
    <div class="boot-logo">H</div>
    <span>正在进入欢乐世界</span>
  </div>
  <AuthScreen v-else-if="!client.authenticated.value" @authenticate="authenticate" />
  <RoomView
    v-else-if="client.screen.value === 'room' && client.session.value?.table"
    :session="client.session.value"
    :execute="execute"
    :ai-runtime="ai.runtime"
    :clear-ai-logs="ai.clearLogs"
    @lobby="client.hideRoom"
    @ai-stop="openStopDialog"
  />
  <LobbyView
    v-else
    :session="client.session.value"
    :modes="client.state.modes"
    :connected="client.state.connected"
    :tab="client.lobbyTab.value"
    :ai-config="client.state.aiConfig"
    :ai-runtime="ai.runtime"
    @tab="client.lobbyTab.value = $event"
    @enter="enterMode"
    @command="execute"
    @qr="client.openQr"
    @logout="logout"
    @ai-start="startAi"
    @ai-stop="openStopDialog"
    @ai-save="ai.save"
    @ai-logs="aiLogOpen = true"
    @ai-provider-test="testAiProvider"
    @watch="client.showRoom"
  />

  <QrModal
    :qr="client.qr"
    :provider="client.state.provider"
    @close="client.qr.open = false"
    @start="client.startQr"
  />
  <ToastStack />
  <AiLogDrawer :open="aiLogOpen" :runtime="ai.runtime" @close="aiLogOpen = false" @clear="ai.clearLogs" />
  <AiStopDialog
    :open="aiStopOpen"
    :hosting-mode="client.state.aiConfig?.hostingMode || ai.runtime.hostingMode"
    :pending="aiStopPending"
    @close="aiStopOpen = false"
    @stop="stopAi"
  />
  <SessionEvictedModal
    :open="client.state.evicted"
    @close="client.state.evicted = false"
    @relogin="relogin"
  />
</template>
