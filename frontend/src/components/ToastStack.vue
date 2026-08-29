<script setup>
import { useToasts } from '@/composables/use-toasts.js'

const { toasts, remove } = useToasts()
</script>

<template>
  <div class="toast-stack" aria-live="polite">
    <TransitionGroup name="toast">
      <button
        v-for="toast in toasts"
        :key="toast.id"
        class="toast-item"
        :class="`is-${toast.tone}`"
        type="button"
        @click="remove(toast.id)"
      >
        <span class="toast-icon">{{ toast.tone === 'error' ? '!' : '✓' }}</span>
        <span>{{ toast.message }}</span>
      </button>
    </TransitionGroup>
  </div>
</template>
