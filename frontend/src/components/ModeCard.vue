<script setup>
import { computed } from 'vue'

const props = defineProps({ mode: { type: Object, required: true }, beans: { type: Number, default: 0 } })
const emit = defineEmits(['enter'])
const number = (value) => new Intl.NumberFormat('zh-CN').format(Number(value || 0))
const familyNames = { CLASSIC: '经典玩法', LAIZI: '癞子玩法', CHALLENGE: '挑战赛' }
const unlimited = computed(() => Number(props.mode.maxBeans) <= 0)
const outside = computed(
  () =>
    props.beans < Number(props.mode.minBeans || 0) ||
    (!unlimited.value && props.beans > Number(props.mode.maxBeans)),
)
</script>

<template>
  <article class="mode-card" :class="[`family-${mode.family.toLowerCase()}`, { locked: outside }]">
    <div class="mode-glow"></div>
    <div class="mode-symbol">
      {{ mode.family === 'LAIZI' ? '癞' : mode.family === 'CHALLENGE' ? '冠' : '斗' }}
    </div>
    <div class="mode-main">
      <span class="mode-family">{{ familyNames[mode.family] || mode.family }}</span>
      <h3>{{ mode.name }}</h3>
      <div class="mode-rules">
        <span>
          <small>参与条件</small>
          {{
            unlimited
              ? `${number(mode.minBeans)} 豆以上`
              : `${number(mode.minBeans)}–${number(mode.maxBeans)} 豆`
          }}
        </span>
        <span><small>本场底分</small>{{ number(mode.baseScore) }}</span>
      </div>
      <div class="mode-bottom">
        <span>{{ mode.cap > 0 ? `封顶 ${number(mode.cap)}` : '竞技结算' }}</span>
        <span>场次 {{ mode.sceneId }}</span>
      </div>
    </div>
    <button class="mode-enter" :disabled="outside" type="button" @click="emit('enter', mode)">
      {{ outside ? '欢乐豆不足' : '快速开始' }}
    </button>
  </article>
</template>
