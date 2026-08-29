<script setup>
import { reactive, ref } from 'vue'
import hostImage from '@/assets/original/host.png'

const emit = defineEmits(['authenticate'])
const tab = ref('login')
const loading = ref(false)
const form = reactive({ username: '', password: '', displayName: '' })

async function submit() {
  loading.value = true
  try {
    await emit('authenticate', tab.value === 'login' ? '/api/auth/login' : '/api/auth/register', {
      username: form.username,
      password: form.password,
      ...(tab.value === 'register' ? { displayName: form.displayName } : {}),
    })
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <div class="auth-cloud cloud-a"></div>
    <div class="auth-cloud cloud-b"></div>
    <section class="auth-brand">
      <div class="brand-lockup">
        <span class="brand-mark">H</span>
        <div><strong>欢乐斗地主</strong><small>HAPPY4J</small></div>
      </div>
      <div class="auth-copy">
        <p class="eyebrow">REAL LOTUS · REAL TABLE</p>
        <h1>经典牌局，<br /><em>现在开场。</em></h1>
        <p>扫码进入真实大厅，完整体验叫地主、加倍、出牌与结算流程。</p>
      </div>
      <img :src="hostImage" alt="欢乐斗地主角色" class="auth-host" />
    </section>

    <section class="auth-card glass-panel">
      <div class="auth-tabs">
        <button :class="{ active: tab === 'login' }" type="button" @click="tab = 'login'">登录</button>
        <button :class="{ active: tab === 'register' }" type="button" @click="tab = 'register'">注册</button>
      </div>
      <div class="auth-heading">
        <span>WELCOME BACK</span>
        <h2>{{ tab === 'login' ? '登录游戏大厅' : '创建玩家账号' }}</h2>
      </div>
      <form @submit.prevent="submit">
        <label>
          <span>账号</span>
          <input v-model.trim="form.username" autocomplete="username" minlength="3" maxlength="32" required />
        </label>
        <label v-if="tab === 'register'">
          <span>玩家昵称</span>
          <input v-model.trim="form.displayName" maxlength="64" placeholder="显示在站点中的名称" />
        </label>
        <label>
          <span>密码</span>
          <input
            v-model="form.password"
            type="password"
            :autocomplete="tab === 'login' ? 'current-password' : 'new-password'"
            minlength="8"
            maxlength="72"
            required
          />
        </label>
        <button class="game-button primary wide" :disabled="loading" type="submit">
          {{ loading ? '正在连接…' : tab === 'login' ? '进入大厅' : '创建并进入' }}
        </button>
      </form>
      <p class="auth-footnote"><i></i> 登录后再使用 QQ 或微信扫码绑定游戏账号</p>
    </section>
  </main>
</template>
