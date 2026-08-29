<script setup>
import { computed, ref } from 'vue'

const props = defineProps({ open: Boolean, runtime: Object })
const emit = defineEmits(['close', 'clear'])
const filter = ref('ALL')
const copied = ref(false)
const filters = [
  ['ALL', '全部'],
  ['DECISION', '决策'],
  ['ERROR', '异常'],
]
const winRate = computed(() => {
  const games = Number(props.runtime?.wins || 0) + Number(props.runtime?.losses || 0)
  return games ? `${Math.round((Number(props.runtime.wins || 0) * 100) / games)}%` : '—'
})

const visibleLogs = computed(() => {
  const logs = [...(props.runtime?.logs || [])].reverse()
  if (filter.value === 'DECISION')
    return logs.filter(
      (entry) => ['DECISION', 'INFERENCE'].includes(entry.phase) || ['PLAY', 'PASS'].includes(entry.level),
    )
  if (filter.value === 'ERROR') return logs.filter((entry) => entry.level === 'ERROR')
  return logs
})

function formatTime(value) {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

function formatScore(value) {
  return value !== null && value !== undefined && Number.isFinite(Number(value))
    ? Number(value).toFixed(4)
    : '—'
}

function cardText(item) {
  if (item.pass) return '不出'
  return item.label || item.cards?.join(' ') || '出牌'
}

async function copyLogs() {
  const text = (props.runtime?.logs || [])
    .map((entry) => {
      const detail = entry.detail ? ` ${JSON.stringify(entry.detail)}` : ''
      return `${entry.at} [${entry.level}] [${entry.phase}] ${entry.message}${detail}`
    })
    .join('\n')
  await navigator.clipboard.writeText(text)
  copied.value = true
  setTimeout(() => (copied.value = false), 1500)
}
</script>

<template>
  <Transition name="ai-log-slide">
    <div v-if="open" class="ai-log-shell" @click.self="emit('close')">
      <aside class="ai-log-drawer">
        <header>
          <div>
            <span class="ai-log-live"><i></i>{{ runtime?.active ? 'LIVE' : 'PAUSED' }}</span>
            <h2>AI 运行日志</h2>
            <p>{{ runtime?.strategy }} · {{ runtime?.status }}</p>
          </div>
          <button type="button" aria-label="关闭日志" @click="emit('close')">×</button>
        </header>

        <section class="ai-log-metrics">
          <div>
            <span>完成局数</span><b>{{ runtime?.rounds || 0 }}</b>
          </div>
          <div>
            <span>本次胜率</span><b>{{ winRate }}</b>
          </div>
          <div>
            <span>胜 / 负</span><b>{{ runtime?.wins || 0 }} / {{ runtime?.losses || 0 }}</b>
          </div>
          <div>
            <span>模型状态</span><b>{{ runtime?.modelState || 'IDLE' }}</b>
          </div>
        </section>

        <nav>
          <button
            v-for="item in filters"
            :key="item[0]"
            :class="{ active: filter === item[0] }"
            type="button"
            @click="filter = item[0]"
          >
            {{ item[1] }}
          </button>
          <span></span>
          <button type="button" @click="copyLogs">{{ copied ? '已复制' : '复制' }}</button>
          <button type="button" @click="emit('clear')">清空</button>
        </nav>

        <div class="ai-log-list">
          <article v-for="entry in visibleLogs" :key="entry.id" :class="`level-${entry.level.toLowerCase()}`">
            <div class="ai-log-row">
              <time>{{ formatTime(entry.at) }}</time>
              <span>{{ entry.level }}</span>
              <b>{{ entry.phase }}</b>
            </div>
            <p>{{ entry.message }}</p>
            <details v-if="entry.detail">
              <summary>查看决策数据</summary>
              <dl>
                <template v-if="entry.detail.candidates !== undefined">
                  <dt>候选动作</dt>
                  <dd>{{ entry.detail.candidates }} 个，其中可出 {{ entry.detail.playableCandidates }} 个</dd>
                  <dt>决策耗时</dt>
                  <dd>{{ entry.detail.elapsedMs }}ms</dd>
                  <dt>选择评分</dt>
                  <dd>{{ formatScore(entry.detail.selectedQ) }}</dd>
                  <template v-if="entry.detail.modelError">
                    <dt>模型错误</dt>
                    <dd>{{ entry.detail.modelError }}</dd>
                  </template>
                </template>
              </dl>
              <div v-if="entry.detail.top?.length" class="ai-q-list">
                <div v-for="(item, index) in entry.detail.top" :key="`${index}-${item.cards?.join('-')}`">
                  <span>#{{ index + 1 }}</span
                  ><strong>{{ cardText(item) }}</strong
                  ><b>{{ formatScore(item.q) }}</b>
                </div>
              </div>
              <pre v-else>{{ JSON.stringify(entry.detail, null, 2) }}</pre>
            </details>
          </article>
          <div v-if="!visibleLogs.length" class="ai-log-empty">
            <i>◎</i><strong>暂无运行日志</strong><span>开始托管后会实时记录牌桌状态与每次决策</span>
          </div>
        </div>
      </aside>
    </div>
  </Transition>
</template>
