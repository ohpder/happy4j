<script setup>
import { computed } from 'vue'

const props = defineProps({
  open: Boolean,
  hostingMode: { type: String, default: 'BROWSER' },
  pending: Boolean,
})
const emit = defineEmits(['close', 'stop'])
const serverHosting = computed(() => props.hostingMode === 'SERVER')
</script>

<template>
  <Transition name="modal">
    <div v-if="open" class="modal-backdrop" @click.self="emit('close')">
      <section class="ai-stop-modal game-modal">
        <button class="modal-close" type="button" aria-label="关闭" @click="emit('close')">×</button>
        <span class="modal-kicker">{{ serverHosting ? 'SERVER HOSTING' : 'AI HOSTING' }}</span>
        <h2>结束托管</h2>
        <p>
          {{
            serverHosting
              ? '选择停止时机。停止请求会保存到服务端，关闭浏览器也不会丢失。'
              : '选择停止时机，当前牌局结束后也可以自动停止。'
          }}
        </p>
        <div class="ai-stop-options">
          <button
            class="game-button soft"
            type="button"
            :disabled="pending"
            @click="emit('stop', 'IMMEDIATE')"
          >
            <strong>立即停止</strong>
            <small>马上停止后续自动操作</small>
          </button>
          <button
            class="game-button gold"
            type="button"
            :disabled="pending"
            @click="emit('stop', 'AFTER_ROUND')"
          >
            <strong>本局结束后停止</strong>
            <small>完成当前牌局并结算后停止</small>
          </button>
        </div>
        <button class="text-button ai-stop-cancel" type="button" :disabled="pending" @click="emit('close')">
          继续托管
        </button>
      </section>
    </div>
  </Transition>
</template>
