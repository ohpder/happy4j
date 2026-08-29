<script setup>
defineProps({
  seconds: { type: Number, default: 25 },
  duration: { type: Number, default: 25 },
  active: Boolean,
})
</script>

<template>
  <div v-if="active" class="turn-timer" :class="{ urgent: seconds <= 5 }">
    <svg viewBox="0 0 44 44" aria-hidden="true">
      <circle cx="22" cy="22" r="19"></circle>
      <circle
        class="timer-progress"
        cx="22"
        cy="22"
        r="19"
        :style="{ '--progress': Math.min(1, seconds / Math.max(1, duration)) }"
      ></circle>
    </svg>
    <strong>{{ seconds }}</strong>
  </div>
</template>
