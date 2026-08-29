<script setup>
import { computed, ref } from 'vue'
import LobbyHeader from '@/components/LobbyHeader.vue'
import AiView from '@/views/AiView.vue'
import ModeCard from '@/components/ModeCard.vue'
import hostImage from '@/assets/original/host.png'

const props = defineProps({
  session: Object,
  modes: { type: Array, default: () => [] },
  connected: Boolean,
  tab: { type: String, default: 'modes' },
  aiConfig: Object,
  aiRuntime: Object,
})
const emit = defineEmits([
  'tab',
  'enter',
  'command',
  'qr',
  'logout',
  'ai-start',
  'ai-stop',
  'ai-save',
  'ai-logs',
  'ai-provider-test',
])
const category = ref('ALL')
const categories = [
  ['ALL', '全部'],
  ['CLASSIC', '经典'],
  ['LAIZI', '癞子'],
  ['CHALLENGE', '挑战'],
]
const visibleModes = computed(() =>
  props.modes.filter((mode) => mode.enabled && (category.value === 'ALL' || mode.family === category.value)),
)
const missions = computed(() => props.session?.missions || [])
const support = computed(() => missions.value.find((mission) => mission.missionId === 1563))
const normalMissions = computed(() => missions.value.filter((mission) => mission.missionId !== 1563))
const walletReady = computed(
  () =>
    Boolean(props.session?.walletRefreshedAt) && !String(props.session.walletRefreshedAt).startsWith('1970-'),
)
const supportEligible = computed(
  () =>
    walletReady.value &&
    Boolean(support.value) &&
    Number(props.session?.beans || 0) < 2000 &&
    support.value.periodFinishCount < 4,
)
const supportLabel = computed(() => {
  if (!walletReady.value) return '余额同步中'
  if (!support.value) return '状态同步中'
  if (support.value.periodFinishCount >= 4) return '今日已领完'
  if (Number(props.session?.beans || 0) >= 2000) return '余额充足，暂不补助'
  return '立即领取'
})
const missionState = { 0: '未开始', 1: '待激活', 2: '进行中', 3: '可领取', 4: '已领取' }
</script>

<template>
  <main class="lobby-page">
    <LobbyHeader
      :session="session"
      :connected="connected"
      :tab="tab"
      @tab="emit('tab', $event)"
      @qr="emit('qr')"
      @logout="emit('logout')"
    />

    <section v-if="tab === 'modes'" class="lobby-content mode-view">
      <aside class="mode-aside">
        <span class="vertical-title">GAME MODE</span>
        <button
          v-for="item in categories"
          :key="item[0]"
          :class="{ active: category === item[0] }"
          type="button"
          @click="category = item[0]"
        >
          <i>{{
            item[0] === 'ALL' ? '♣' : item[0] === 'LAIZI' ? '癞' : item[0] === 'CHALLENGE' ? '★' : '♠'
          }}</i>
          {{ item[1] }}
        </button>
      </aside>

      <div class="mode-stage">
        <div class="stage-heading">
          <div>
            <span>SELECT ARENA</span>
            <h1>选择你的牌局</h1>
          </div>
          <p>根据欢乐豆余额自动开放可进入场次</p>
        </div>
        <div class="mode-grid">
          <ModeCard
            v-for="mode in visibleModes"
            :key="mode.sceneId"
            :mode="mode"
            :beans="Number(session?.beans || 0)"
            @enter="emit('enter', $event)"
          />
        </div>
      </div>
      <img :src="hostImage" class="lobby-host" alt="欢乐斗地主角色" />
    </section>

    <AiView
      v-else-if="tab === 'ai'"
      :config="aiConfig"
      :modes="modes"
      :session="session"
      :runtime="aiRuntime"
      @start="emit('ai-start', $event)"
      @stop="emit('ai-stop')"
      @save="emit('ai-save', $event)"
      @logs="emit('ai-logs')"
      @provider-test="emit('ai-provider-test', $event)"
    />

    <section v-else class="lobby-content reward-view">
      <div class="reward-heading">
        <div>
          <span>DAILY REWARDS</span>
          <h1>活动与奖励</h1>
          <p>完成任务后直接领取，结果会以提示反馈。</p>
        </div>
        <div class="reward-toolbar">
          <button
            class="game-button soft"
            type="button"
            @click="emit('command', 'ACTIVITIES_REFRESH', {}, '活动已刷新')"
          >
            刷新活动
          </button>
          <button
            class="game-button gold"
            type="button"
            @click="emit('command', 'SIGN_IN_CLAIM', { commandCode: 13, prizeIndex: 0 }, '签到奖励领取成功')"
          >
            每日签到
          </button>
        </div>
      </div>

      <article class="support-card">
        <div class="support-coin">豆</div>
        <div>
          <span>DAILY SUPPORT</span>
          <h2>每日欢乐豆补助</h2>
          <p>每天最多领取四次，余额不足时可继续挑战。</p>
        </div>
        <div class="support-progress">
          <b>{{ support?.periodFinishCount || 0 }}</b
          ><span>/ 4</span>
        </div>
        <button
          class="game-button gold"
          :disabled="!supportEligible"
          type="button"
          @click="emit('command', 'MISSION_CLAIM', { missionId: 1563 }, '欢乐豆领取成功')"
        >
          {{ supportLabel }}
        </button>
      </article>

      <div class="reward-columns">
        <section>
          <div class="section-title">
            <h2>任务中心</h2>
            <span>{{ normalMissions.length }} 项</span>
          </div>
          <div class="mission-list">
            <article v-for="mission in normalMissions" :key="mission.missionId" class="mission-row">
              <div class="mission-medal">✦</div>
              <div>
                <strong>{{ mission.title }}</strong
                ><small>已完成 {{ mission.periodFinishCount || 0 }} 次</small>
              </div>
              <span class="mission-state" :class="{ claimable: mission.claimable }">{{
                missionState[mission.status] || '进行中'
              }}</span>
              <button
                v-if="mission.claimable"
                type="button"
                @click="
                  emit('command', 'MISSION_CLAIM', { missionId: mission.missionId }, '任务奖励领取成功')
                "
              >
                领取
              </button>
            </article>
            <p v-if="!normalMissions.length" class="empty-copy">登录游戏账号后同步任务</p>
          </div>
        </section>

        <section>
          <div class="section-title">
            <h2>热门活动</h2>
            <span>LIVE</span>
          </div>
          <div class="activity-list">
            <article v-for="activity in session?.activities || []" :key="activity.id" class="activity-tile">
              <i>{{ activity.id === 'sign-in' ? '签' : activity.id === 'competition' ? '赛' : '惠' }}</i>
              <div>
                <strong>{{ activity.title }}</strong
                ><small>{{ activity.status === 'READY' ? '当前可参与' : '暂未开放' }}</small>
              </div>
              <span>{{ activity.status === 'READY' ? '前往' : '—' }}</span>
            </article>
            <p v-if="!session?.activities?.length" class="empty-copy">刷新后显示可参与活动</p>
          </div>
        </section>
      </div>
    </section>
  </main>
</template>
