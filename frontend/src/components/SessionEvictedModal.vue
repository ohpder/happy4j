<script setup>
defineProps({ open: Boolean })
const emit = defineEmits(['close', 'relogin'])
</script>

<template>
  <Transition name="modal">
    <div v-if="open" class="modal-backdrop">
      <section class="evicted-modal game-modal">
        <div class="evicted-icon">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M12 3a6 6 0 0 0-6 6v3l-2 3h16l-2-3V9a6 6 0 0 0-6-6Z" />
            <path d="M9 19h6M12 8v3M12 14h.01" />
          </svg>
        </div>
        <span class="modal-kicker">ACCOUNT NOTICE</span>
        <h2>账号已在其他设备登录</h2>
        <p>为了保护你的牌局，本设备已经退出游戏账号。重新扫码后即可继续。</p>
        <div class="evicted-actions">
          <button class="game-button soft" type="button" @click="emit('close')">稍后处理</button>
          <button class="game-button gold" type="button" @click="emit('relogin')">重新扫码</button>
        </div>
      </section>
    </div>
  </Transition>
</template>
