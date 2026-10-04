<script setup lang="ts">
// PG-07 我的硬币子页（06 §3.7.4，Phase 3 真实化，FR-IN11）：
// 余额展示（US01 coin，投币后经 /users/me 刷新联动）+ 每日签到入口（AU07，当日已签置灰）
// + 硬币流水明细（US06 分页倒序，biz_type 中文映射、收支正负色、「上一页/下一页」翻页）。
// 「当日已签」由 US06 首页流水中是否存在今日 bizType=1 推导（无独立字段；后端 40906 兜底）。
import { computed, onMounted, ref } from 'vue'
import { getMyProfile, getMyCoinFlows, checkIn } from '@/api'
import type { CoinFlowItem } from '@/types/api'
import { toast } from '@/utils/toast'

// ---- 页面三态 ----
const loading = ref(true)
const loadError = ref('')

const coin = ref(0)
const coinFlash = ref(false) // 签到成功余额数字高亮动效（前端从简）

// ---- 流水（US06 分页倒序）----
const flows = ref<CoinFlowItem[]>([])
const total = ref(0)
const page = ref(1)
const PAGE_SIZE = 20
const flowsLoading = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

/** biz_type 中文映射（SSOT：1 签到收入 / 2 注册赠送 / 3 投币支出） */
const BIZ_LABELS: Record<number, string> = { 1: '签到收入', 2: '注册赠送', 3: '投币支出' }

/** 当日已签：首页流水中存在今日 bizType=1 行（首页无则视为未签，后端 40906 兜底——FR-IN11 设计备注） */
const todaySigned = computed(() => {
  const today = new Date()
  const pad = (x: number) => String(x).padStart(2, '0')
  const todayStr = `${today.getFullYear()}-${pad(today.getMonth() + 1)}-${pad(today.getDate())}`
  return flows.value.some((f) => f.bizType === 1 && f.createdAt.startsWith(todayStr))
})

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const p = await getMyProfile()
    coin.value = p.coin
    await loadFlows(1)
  } catch (e) {
    loadError.value = (e as Error).message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadFlows(p: number) {
  flowsLoading.value = true
  try {
    const resp = await getMyCoinFlows(p, PAGE_SIZE)
    page.value = resp.page
    total.value = resp.total
    flows.value = resp.list
  } catch (e) {
    toast((e as Error).message || '流水加载失败')
  } finally {
    flowsLoading.value = false
  }
}

// ---- 每日签到（AU07：+5 枚；成功置灰 + 余额联动 + 流水头部插入）----
const checkingIn = ref(false)

async function onCheckIn() {
  if (checkingIn.value || todaySigned.value) return
  checkingIn.value = true
  try {
    const resp = await checkIn()
    coin.value = resp.coin
    coinFlash.value = true
    setTimeout(() => (coinFlash.value = false), 1500)
    // 流水头部插入（本地拼装，时间取当下；翻页后自然被服务端数据校准）
    const now = new Date()
    const pad = (x: number) => String(x).padStart(2, '0')
    flows.value.unshift({
      flowId: -Date.now(), // 临时 id（仅 :key 用）
      changeAmount: 5,
      bizType: 1,
      createdAt: `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`,
    })
    total.value += 1
    toast('签到成功，获得 5 枚硬币')
  } catch (e) {
    toast((e as Error).message || '签到失败，请重试') // 40906 兜底（并发/跨页判断偏差）
  } finally {
    checkingIn.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="coin-page">
    <!-- 加载态 -->
    <div v-if="loading" class="state-box">加载中…</div>

    <!-- 加载失败 -->
    <div v-else-if="loadError" class="state-box">
      <p>{{ loadError }}</p>
      <button class="retry-btn" @click="load">重试</button>
    </div>

    <!-- 正常态：余额 + 签到入口 + 流水明细 -->
    <div v-else>
      <div class="head-row">
        <h2 class="sub-title">我的硬币</h2>
        <!-- 每日签到：当日已签置灰（FR-IN11） -->
        <button class="checkin-btn" :class="{ done: todaySigned }" :disabled="todaySigned || checkingIn" @click="onCheckIn">
          {{ todaySigned ? '今日已签到' : checkingIn ? '签到中…' : '签到领取 +5' }}
        </button>
      </div>
      <div class="balance-box">
        <span class="coin-icon" aria-hidden="true"></span>
        <span class="balance" :class="{ flash: coinFlash }">{{ coin }}</span>
      </div>

      <!-- 流水明细：类型 + 金额（收支正负色）+ 时间；上一页/下一页翻页 -->
      <div class="flow-box">
        <h3 class="flow-title">硬币流水</h3>
        <div v-if="flowsLoading" class="flow-state">加载中…</div>
        <div v-else-if="flows.length === 0" class="flow-state">暂无记录</div>
        <template v-else>
          <div v-for="f in flows" :key="f.flowId" class="flow-row">
            <span class="flow-label">{{ BIZ_LABELS[f.bizType] ?? `类型${f.bizType}` }}</span>
            <span class="flow-amount" :class="f.changeAmount >= 0 ? 'in' : 'out'">
              {{ f.changeAmount >= 0 ? '+' : '' }}{{ f.changeAmount }}
            </span>
            <span class="flow-time">{{ f.createdAt }}</span>
          </div>
          <div class="flow-pager">
            <button :disabled="page <= 1 || flowsLoading" @click="loadFlows(page - 1)">上一页</button>
            <span>第 {{ page }} / {{ totalPages }} 页</span>
            <button :disabled="page >= totalPages || flowsLoading" @click="loadFlows(page + 1)">下一页</button>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.state-box {
  min-height: 300px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-block);
  color: var(--color-text-secondary);
}
.state-box p {
  margin: 0;
}
.retry-btn {
  padding: 8px 24px;
  border: 1px solid var(--color-primary);
  color: var(--color-primary);
  background: none;
  border-radius: var(--radius-card);
  cursor: pointer;
}
.retry-btn:hover {
  background: var(--color-primary);
  color: #fff;
}
/* 标题行 + 签到按钮 */
.head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.sub-title {
  margin: 0;
  font-size: 18px;
}
.checkin-btn {
  padding: 8px 24px;
  border: none;
  border-radius: 999px;
  background: var(--color-primary);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}
.checkin-btn:hover:not(:disabled) {
  opacity: 0.9;
}
.checkin-btn:disabled {
  background: var(--color-border);
  color: var(--color-text-secondary);
  cursor: not-allowed;
}
/* 余额 */
.balance-box {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 0 20px;
}
/* CSS 硬币图形（ADR-05：无二进制资源）：圆 + 内描边 */
.coin-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--color-primary-light);
  border: 3px solid var(--color-primary);
  box-shadow: inset 0 0 0 4px var(--color-card);
  flex-shrink: 0;
}
.balance {
  font-size: 48px;
  font-weight: 700;
  line-height: 1;
}
/* 签到成功余额高亮动效（发放动效，前端从简） */
.balance.flash {
  color: var(--color-primary);
  animation: coin-pop 0.6s ease;
}
@keyframes coin-pop {
  0% { transform: scale(1); }
  40% { transform: scale(1.25); }
  100% { transform: scale(1); }
}
/* 流水明细 */
.flow-box {
  margin-top: 8px;
  padding: 20px;
  background: var(--color-bg);
  border-radius: var(--radius-card);
}
.flow-title {
  margin: 0 0 12px;
  font-size: 15px;
}
.flow-state {
  padding: 24px 0;
  text-align: center;
  color: var(--color-text-secondary);
  font-size: 14px;
}
.flow-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-border);
  font-size: 14px;
}
.flow-row:last-of-type {
  border-bottom: none;
}
.flow-label {
  flex: 1;
}
.flow-amount {
  width: 64px;
  text-align: right;
  font-weight: 600;
}
.flow-amount.in {
  color: var(--color-primary);
}
.flow-amount.out {
  color: var(--color-danger);
}
.flow-time {
  color: var(--color-text-secondary);
  font-size: 12px;
}
.flow-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 14px;
}
.flow-pager span {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.flow-pager button {
  padding: 6px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-card);
  color: var(--color-text);
  font-size: 13px;
  cursor: pointer;
}
.flow-pager button:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.flow-pager button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
