<script setup>
import { isRedCard } from '@/utils/cards.js'

defineProps({
  card: { type: Object, required: true },
  selected: Boolean,
  index: { type: Number, default: 0 },
  count: { type: Number, default: 1 },
  compact: Boolean,
  overlap: { type: Number, default: null },
})
const emit = defineEmits(['drag-start', 'drag-enter'])
</script>

<template>
  <button
    class="poker-card"
    :class="{ selected, red: isRedCard(card), joker: card.code >= 53, compact }"
    :style="{
      '--card-index': index,
      '--card-count': count,
      ...(overlap !== null && index > 0 ? { marginLeft: `${overlap}px` } : {}),
    }"
    type="button"
    :aria-label="`${card.rank}${card.suit}`"
    :data-card-code="card.code"
    @pointerdown.prevent="emit('drag-start', card.code)"
    @pointerenter="emit('drag-enter', card.code)"
  >
    <span class="card-corner"
      ><strong>{{ card.rank }}</strong
      ><small>{{ card.suit }}</small></span
    >
    <span v-if="card.code < 53" class="card-center">{{ card.suit }}</span>
    <span v-else class="joker-word">{{ card.code === 54 ? 'BIG JOKER' : 'JOKER' }}</span>
    <span class="card-shine"></span>
  </button>
</template>
