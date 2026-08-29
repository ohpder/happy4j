<script setup>
defineProps({ counts: { type: Array, default: () => [] }, open: Boolean })
const emit = defineEmits(['close'])
</script>

<template>
  <Transition name="counter">
    <aside v-if="open" class="card-counter">
      <div class="counter-head">
        <span>记牌器</span><button type="button" @click="emit('close')">×</button>
      </div>
      <div class="counter-grid">
        <div v-for="item in counts" :key="item.label" :class="{ empty: item.count === 0 }">
          <strong>{{ item.label }}</strong
          ><span>{{ item.count }}</span>
        </div>
      </div>
      <p>统计未在你手中且尚未打出的牌</p>
    </aside>
  </Transition>
</template>
