<script setup>
import defaultAvatar from '@/assets/original/default-avatar.png'
import TurnTimer from './TurnTimer.vue'

defineProps({
  player: Object,
  landlord: Boolean,
  active: Boolean,
  seconds: { type: Number, default: 25 },
  duration: { type: Number, default: 25 },
  side: { type: String, default: 'left' },
})
const number = (value) => new Intl.NumberFormat('zh-CN').format(Number(value || 0))
function avatarFallback(event) {
  if (event.currentTarget.src !== defaultAvatar) event.currentTarget.src = defaultAvatar
}
</script>

<template>
  <div class="player-seat" :class="[`seat-${side}`, { active }]">
    <div class="player-avatar">
      <img :src="player?.avatarUrl || defaultAvatar" alt="玩家头像" @error="avatarFallback" />
      <span v-if="landlord" class="landlord-badge"><i>♛</i>地主</span>
    </div>
    <TurnTimer :seconds="seconds" :duration="duration" :active="active" />
    <div class="player-info">
      <strong>{{ player?.nickname || '等待玩家' }}</strong>
      <span><i>豆</i>{{ number(player?.beans) }}</span>
    </div>
    <div class="opponent-hand" aria-label="剩余手牌">
      <div class="opponent-cards">
        <i v-for="index in Math.min(player?.cardCount || 0, 8)" :key="index"></i>
      </div>
      <b class="seat-card-count">剩 {{ player?.cardCount ?? 0 }} 张</b>
    </div>
    <div v-if="player?.ready" class="ready-stamp">已准备</div>
  </div>
</template>
