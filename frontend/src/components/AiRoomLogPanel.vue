<script setup>
import { computed } from 'vue'

const props = defineProps({
  open: Boolean,
  runtime: Object,
})
const emit = defineEmits(['toggle', 'clear'])

const compactLogs = computed(() =>
  [...(props.runtime?.logs || [])]
    .filter(
      (entry) =>
        ['ERROR', 'PLAY', 'PASS', 'DECISION', 'STATE'].includes(entry.level) ||
        ['INFERENCE', 'CALLING_LANDLORD', 'DOUBLING', 'SETTLEMENT'].includes(entry.phase),
    )
    .slice(-7)
    .reverse(),
)

function time(value) {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

function score(entry) {
  const raw = entry.detail?.selectedQ
  const value = Number(raw)
  return raw !== null && raw !== undefined && Number.isFinite(value) ? `Q ${value.toFixed(3)}` : ''
}

function candidate(entry) {
  if (entry.detail?.candidates === undefined) return ''
  return `${entry.detail.playableCandidates}/${entry.detail.candidates} 可出`
}

function winRate(runtime) {
  const games = Number(runtime?.wins || 0) + Number(runtime?.losses || 0)
  return games ? `${Math.round((Number(runtime.wins || 0) * 100) / games)}%` : '—'
}
</script>

<template>
  <section v-if="open" class="ai-room-mini-log">
    <header>
      <div>
        <i></i><strong>AI 日志</strong><span>{{ runtime?.strategy }}</span>
      </div>
      <button type="button" aria-label="收起AI日志" @click="emit('toggle')">−</button>
    </header>
    <div class="ai-room-mini-metrics">
      <span
        >局 <b>{{ runtime?.rounds || 0 }}</b></span
      >
      <span
        >胜 <b>{{ winRate(runtime) }}</b></span
      >
      <span
        >出 <b>{{ runtime?.plays || 0 }}</b></span
      >
      <span
        >过 <b>{{ runtime?.passes || 0 }}</b></span
      >
      <span class="model" :class="runtime?.modelState?.toLowerCase()">{{
        runtime?.modelState || 'IDLE'
      }}</span>
    </div>
    <div class="ai-room-mini-feed">
      <article v-for="entry in compactLogs" :key="entry.id" :class="`mini-${entry.level.toLowerCase()}`">
        <time>{{ time(entry.at) }}</time>
        <div>
          <p>{{ entry.message }}</p>
          <small v-if="candidate(entry) || score(entry)">{{ candidate(entry) }} {{ score(entry) }}</small>
        </div>
      </article>
      <p v-if="!compactLogs.length" class="ai-room-mini-empty">等待第一条牌桌决策…</p>
    </div>
    <footer>
      <span>{{ runtime?.status }}</span>
      <button type="button" @click="emit('clear')">清空</button>
    </footer>
  </section>
  <button v-else class="ai-room-mini-collapsed" type="button" @click="emit('toggle')">
    <i></i><span>AI 日志</span><b>{{ runtime?.logs?.length || 0 }}</b>
  </button>
</template>
