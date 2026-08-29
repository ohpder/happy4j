<script setup>
import { computed, reactive, watch } from 'vue'

const props = defineProps({
  config: Object,
  modes: { type: Array, default: () => [] },
  session: Object,
  runtime: Object,
  standalone: Boolean,
})
const emit = defineEmits(['start', 'stop', 'save', 'logs', 'provider-test'])

const draft = reactive({
  sceneId: 0,
  autoChangeTable: true,
  autoClaimBeans: true,
  stopAtBeans: 0,
  stopBelowBeans: 0,
  inferenceEngine: 'DOUZERO',
  apiBaseUrl: '',
  apiModel: '',
  apiKey: '',
  apiKeyConfigured: false,
  clearApiKey: false,
})
const errors = reactive({ message: '' })
const classicModes = computed(() => props.modes.filter((mode) => mode.enabled && mode.family === 'CLASSIC'))
const active = computed(() => Boolean(props.runtime?.active))
const selectedMode = computed(() =>
  classicModes.value.find((mode) => Number(mode.sceneId) === Number(draft.sceneId)),
)

watch(
  () => props.config,
  (value) => {
    if (!value) return
    Object.assign(draft, {
      sceneId: Number(value.sceneId || 0),
      autoChangeTable: Boolean(value.autoChangeTable),
      autoClaimBeans: Boolean(value.autoClaimBeans),
      stopAtBeans: Number(value.stopAtBeans || 0),
      stopBelowBeans: Number(value.stopBelowBeans || 0),
      inferenceEngine: value.inferenceEngine || 'DOUZERO',
      apiBaseUrl: value.apiBaseUrl || '',
      apiModel: value.apiModel || '',
      apiKey: '',
      apiKeyConfigured: Boolean(value.apiKeyConfigured),
      clearApiKey: false,
    })
  },
  { immediate: true },
)

watch(
  classicModes,
  (modes) => {
    if (modes.length && !modes.some((mode) => Number(mode.sceneId) === Number(draft.sceneId))) {
      const beans = Number(props.session?.beans || 0)
      const suitable = modes.find(
        (mode) => beans >= Number(mode.minBeans || 0) && (!mode.maxBeans || beans <= Number(mode.maxBeans)),
      )
      draft.sceneId = Number((suitable || modes[0]).sceneId)
    }
  },
  { immediate: true },
)

function payload() {
  return {
    sceneId: Number(draft.sceneId),
    autoChangeTable: draft.autoChangeTable,
    autoClaimBeans: draft.autoClaimBeans,
    stopAtBeans: Math.max(0, Number(draft.stopAtBeans || 0)),
    stopBelowBeans: Math.max(0, Number(draft.stopBelowBeans || 0)),
    inferenceEngine: draft.inferenceEngine,
    apiBaseUrl: draft.apiBaseUrl.trim(),
    apiModel: draft.apiModel.trim(),
    apiKey: draft.apiKey.trim(),
    clearApiKey: draft.clearApiKey,
  }
}

function start() {
  const values = payload()
  if (!values.sceneId) errors.message = '请选择游戏模式'
  else if (values.stopAtBeans > 0 && values.stopBelowBeans > 0 && values.stopBelowBeans >= values.stopAtBeans)
    errors.message = '最低欢乐豆必须小于目标欢乐豆'
  else if (values.inferenceEngine === 'OPENAI' && !values.apiBaseUrl) errors.message = '请填写OpenAI兼容URL'
  else if (values.inferenceEngine === 'OPENAI' && !values.apiModel) errors.message = '请填写大模型名称'
  else {
    errors.message = ''
    emit('start', values)
  }
}

function testProvider() {
  const values = payload()
  if (!values.apiBaseUrl) errors.message = '请填写OpenAI兼容URL'
  else if (!values.apiModel) errors.message = '请填写大模型名称'
  else {
    errors.message = ''
    emit('provider-test', values)
  }
}

function clearApiKey() {
  draft.apiKey = ''
  draft.apiKeyConfigured = false
  draft.clearApiKey = true
}
</script>

<template>
  <section class="ai-control" :class="{ running: active, standalone }">
    <div class="ai-identity">
      <span class="ai-orbit"><i></i></span>
      <div>
        <div class="ai-title-line">
          <strong>{{ draft.inferenceEngine === 'OPENAI' ? 'OpenAI LLM' : 'DouZero FullAuto ResNet' }}</strong>
          <small>{{ draft.inferenceEngine === 'OPENAI' ? '兼容 API' : '欢乐斗地主 ADP 端侧 ONNX' }}</small>
        </div>
        <p>{{ runtime?.status || '配置后点击开始' }}</p>
        <span v-if="runtime?.decision" class="ai-decision">{{ runtime.decision }}</span>
      </div>
    </div>

    <label class="ai-field mode-field">
      <span>游戏模式</span>
      <select v-model.number="draft.sceneId" :disabled="active">
        <option v-for="mode in classicModes" :key="mode.sceneId" :value="mode.sceneId">
          {{ mode.name }} · {{ Number(mode.baseScore || 0).toLocaleString() }} 底分
        </option>
      </select>
      <small v-if="selectedMode">准入 {{ Number(selectedMode.minBeans || 0).toLocaleString() }} 豆</small>
    </label>

    <div class="ai-switches">
      <label>
        <input v-model="draft.autoChangeTable" type="checkbox" :disabled="active" />
        <span></span>
        自动换桌
      </label>
      <label>
        <input v-model="draft.autoClaimBeans" type="checkbox" :disabled="active" />
        <span></span>
        自动领豆
      </label>
    </div>

    <label class="ai-field bean-limit">
      <span>达到后停止</span>
      <div>
        <input v-model.number="draft.stopAtBeans" min="0" step="1000" type="number" :disabled="active" /><b
          >豆</b
        >
      </div>
      <small>0 表示持续运行</small>
    </label>

    <label class="ai-field bean-limit">
      <span>少于时停止</span>
      <div>
        <input v-model.number="draft.stopBelowBeans" min="0" step="1000" type="number" :disabled="active" /><b
          >豆</b
        >
      </div>
      <small>领豆后仍不足才停止</small>
    </label>

    <div class="ai-actions">
      <button v-if="!active" class="ai-start" type="button" @click="start"><span>▶</span> 开始托管</button>
      <button v-else class="ai-stop" type="button" @click="emit('stop')"><span>■</span> 停止托管</button>
      <button class="ai-log-open" type="button" @click="emit('logs')">
        <i></i>运行日志 <b>{{ runtime?.logs?.length || 0 }}</b>
      </button>
    </div>
    <div v-if="standalone" class="ai-provider-strip">
      <label class="ai-field engine-field">
        <span>推理引擎</span>
        <select v-model="draft.inferenceEngine" :disabled="active">
          <option value="OPENAI">OpenAI兼容大模型</option>
          <option value="DOUZERO">本地DouZero FullAuto ResNet ADP</option>
        </select>
        <small>{{
          draft.inferenceEngine === 'OPENAI' ? '服务端安全调用，不向浏览器返回密钥' : '浏览器端本地推理'
        }}</small>
      </label>
      <template v-if="draft.inferenceEngine === 'OPENAI'">
        <label class="ai-field provider-url-field">
          <span>API URL</span>
          <input
            v-model.trim="draft.apiBaseUrl"
            type="url"
            placeholder="https://api.openai.com/v1"
            :disabled="active"
          />
          <small>支持Base URL或完整/chat/completions地址</small>
        </label>
        <label class="ai-field provider-model-field">
          <span>模型</span>
          <input v-model.trim="draft.apiModel" type="text" placeholder="gpt-5.4" :disabled="active" />
          <small>填写服务端实际开放的模型ID</small>
        </label>
        <label class="ai-field provider-key-field">
          <span>API Key</span>
          <div>
            <input
              v-model="draft.apiKey"
              type="password"
              :placeholder="draft.apiKeyConfigured ? '已安全保存，留空保持原值' : 'sk-...（本地服务可留空）'"
              :disabled="active"
              @input="draft.clearApiKey = false"
            />
            <button v-if="draft.apiKeyConfigured && !active" type="button" @click="clearApiKey">清除</button>
          </div>
          <small>AES-GCM加密保存，页面仅显示配置状态</small>
        </label>
        <button class="provider-test-button" type="button" :disabled="active" @click="testProvider">
          {{ runtime?.providerTesting ? '测试中…' : '测试连接' }}
        </button>
      </template>
    </div>
    <p v-if="errors.message" class="ai-error">{{ errors.message }}</p>
  </section>
</template>
