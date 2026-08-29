<script setup>
defineProps({ table: { type: Object, required: true } })
const emit = defineEmits(['action'])
</script>

<template>
  <div class="modal-backdrop table-timeout-backdrop">
    <section class="table-timeout-modal game-modal">
      <div class="timeout-clock" aria-hidden="true"><span>⌛</span></div>
      <span class="modal-kicker">TABLE PAUSED</span>
      <h2>本桌等待超时</h2>
      <p>当前牌桌等待时间较长，换一桌即可继续游戏。</p>
      <div class="timeout-table-info">
        <span
          ><small>场次</small><b>{{ table.sceneId || '—' }}</b></span
        >
        <i></i>
        <span
          ><small>桌号</small><b>{{ table.tableId || '—' }}</b></span
        >
      </div>
      <div class="timeout-actions">
        <button
          v-if="table.availableActions.includes('CHANGE_TABLE')"
          class="game-button gold"
          type="button"
          @click="emit('action', 'CHANGE_TABLE')"
        >
          立即换桌
        </button>
        <button
          v-if="table.availableActions.includes('LEAVE_ROOM')"
          class="game-button soft"
          type="button"
          @click="emit('action', 'LEAVE_ROOM')"
        >
          返回大厅
        </button>
      </div>
    </section>
  </div>
</template>
