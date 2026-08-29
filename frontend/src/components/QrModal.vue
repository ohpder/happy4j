<script setup>
defineProps({ qr: { type: Object, required: true }, provider: { type: String, default: 'qq' } })
const emit = defineEmits(['close', 'start'])
</script>

<template>
  <Transition name="modal">
    <div v-if="qr.open" class="modal-backdrop" @click.self="emit('close')">
      <section class="qr-modal game-modal">
        <button class="modal-close" type="button" aria-label="关闭" @click="emit('close')">×</button>
        <span class="modal-kicker">GAME ACCOUNT</span>
        <h2>扫码登录游戏账号</h2>
        <div class="provider-switch">
          <button :class="{ active: provider === 'qq' }" type="button" @click="emit('start', 'qq')">
            <b class="provider-qq">Q</b> QQ 扫码
          </button>
          <button :class="{ active: provider === 'wechat' }" type="button" @click="emit('start', 'wechat')">
            <b class="provider-wx">◉</b> 微信扫码
          </button>
        </div>
        <div class="qr-stage" :class="{ loading: qr.loading }">
          <img v-if="qr.image" :src="qr.image" alt="登录二维码" />
          <div v-else class="qr-placeholder">
            <span>⌁</span>
            <p>选择登录方式生成二维码</p>
          </div>
        </div>
        <p class="qr-status"><i></i>{{ qr.status }}</p>
      </section>
    </div>
  </Transition>
</template>
