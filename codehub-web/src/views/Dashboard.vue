<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { listMyRepos, listPublicRepos } from '../api/repo'
import type { RepoVO } from '../api/repo'

const router = useRouter()
const userStore = useUserStore()

const myRepos = ref<RepoVO[]>([])
const publicRepos = ref<RepoVO[]>([])
const myTotal = ref(0)
const publicTotal = ref(0)
const loading = ref(true)

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

async function loadDashboardData() {
  loading.value = true
  try {
    const [myRes, pubRes] = await Promise.allSettled([
      listMyRepos(1, 6),
      listPublicRepos(1, 6)
    ])
    if (myRes.status === 'fulfilled') {
      myRepos.value = myRes.value.records
      myTotal.value = myRes.value.total
    }
    if (pubRes.status === 'fulfilled') {
      publicRepos.value = pubRes.value.records
      publicTotal.value = pubRes.value.total
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadDashboardData()
})
</script>

<template>
  <div class="dashboard-page animate-fade-in">
    <!-- 顶部欢迎横幅 -->
    <div class="welcome-banner">
      <div class="welcome-text">
        <h1 class="greeting-title">
          {{ greeting }}，{{ userStore.user?.username || '开发者' }} 👋
        </h1>
        <p class="greeting-sub">
          欢迎使用 CodeHub 智能代码托管与理解平台。基于大模型 RAG 与高可靠消息队列，助您高效协同与检索代码。
        </p>
      </div>

      <div class="welcome-actions">
        <el-button
          type="primary"
          size="large"
          class="action-btn"
          @click="router.push('/repositories')"
        >
          <el-icon><Plus /></el-icon>
          新建代码仓库
        </el-button>
        <el-button
          size="large"
          class="action-btn-secondary"
          @click="router.push('/repositories')"
        >
          <el-icon><Folder /></el-icon>
          浏览全部仓库
        </el-button>
      </div>
    </div>

    <!-- 4 个多维指标卡片 -->
    <div class="metrics-grid">
      <div class="metric-card">
        <div class="metric-icon-box icon-purple">
          <el-icon><FolderOpened /></el-icon>
        </div>
        <div class="metric-info">
          <div class="metric-label">我的代码库</div>
          <div class="metric-value">{{ myTotal }}</div>
          <div class="metric-trend text-primary">独立拥有或参与</div>
        </div>
      </div>

      <div class="metric-card">
        <div class="metric-icon-box icon-blue">
          <el-icon><Share /></el-icon>
        </div>
        <div class="metric-info">
          <div class="metric-label">全站公共仓库</div>
          <div class="metric-value">{{ publicTotal }}</div>
          <div class="metric-trend text-info">开源代码可自由克隆</div>
        </div>
      </div>

      <div class="metric-card">
        <div class="metric-icon-box icon-emerald">
          <el-icon><ChatDotRound /></el-icon>
        </div>
        <div class="metric-info">
          <div class="metric-label">AI 语义问答</div>
          <div class="metric-value">Qwen 3.7+</div>
          <div class="metric-trend text-success">NDJSON 流式 RAG 就绪</div>
        </div>
      </div>

      <div class="metric-card">
        <div class="metric-icon-box icon-amber">
          <el-icon><Check /></el-icon>
        </div>
        <div class="metric-info">
          <div class="metric-label">P0 可靠性体系</div>
          <div class="metric-value">100%</div>
          <div class="metric-trend text-warning">RabbitMQ + 幂等 + Redisson</div>
        </div>
      </div>
    </div>

    <!-- 下方双栏布局 -->
    <div class="content-grid">
      <!-- 左栏：最近活跃仓库 -->
      <div class="content-left">
        <el-card shadow="never" class="section-card">
          <template #header>
            <div class="section-header">
              <div class="section-title">
                <el-icon><Document /></el-icon>
                <span>我的近期仓库</span>
              </div>
              <el-button link type="primary" @click="router.push('/repositories')">
                查看全部 <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>
          </template>

          <div v-if="loading" class="loading-box">
            <el-skeleton :rows="4" animated />
          </div>

          <div v-else-if="myRepos.length === 0" class="empty-state">
            <div class="empty-icon"><el-icon><FolderAdd /></el-icon></div>
            <div class="empty-title">暂无代码仓库</div>
            <p class="empty-desc">立即创建您的第一个代码仓库，体验大模型智能问答</p>
            <el-button type="primary" @click="router.push('/repositories')">创建仓库</el-button>
          </div>

          <div v-else class="repo-list">
            <div
              v-for="repo in myRepos"
              :key="repo.id"
              class="repo-item"
              @click="router.push(`/repos/${repo.id}`)"
            >
              <div class="repo-main">
                <div class="repo-title-row">
                  <span class="repo-name">{{ repo.name }}</span>
                  <span :class="['vis-pill', repo.visibility === 'PUBLIC' ? 'vis-public' : 'vis-private']">
                    {{ repo.visibility === 'PUBLIC' ? 'Public' : 'Private' }}
                  </span>
                </div>
                <div class="repo-desc">
                  {{ repo.description || '暂无描述信息' }}
                </div>
                <div class="repo-meta">
                  <span class="meta-item">
                    <el-icon><User /></el-icon> {{ repo.ownerName || 'owner' }}
                  </span>
                  <span class="meta-item">
                    <el-icon><Star /></el-icon> {{ repo.starCount || 0 }}
                  </span>
                  <span class="meta-item">
                    <el-icon><Clock /></el-icon> {{ (repo.updatedAt || '').substring(0, 10) }}
                  </span>
                </div>
              </div>
              <el-icon class="repo-arrow"><ArrowRight /></el-icon>
            </div>
          </div>
        </el-card>
      </div>

      <!-- 右栏：架构可靠性与快速指引 -->
      <div class="content-right">
        <!-- 架构可靠性亮点卡片 -->
        <el-card shadow="never" class="section-card">
          <template #header>
            <div class="section-header">
              <div class="section-title">
                <el-icon><Cpu /></el-icon>
                <span>P0 高可靠性架构亮点</span>
              </div>
              <span class="active-badge">8/8 测试通过</span>
            </div>
          </template>

          <div class="tech-specs-list">
            <div class="tech-spec-item">
              <div class="tech-dot bg-indigo"></div>
              <div class="tech-content">
                <div class="tech-title">RabbitMQ 异步文件索引解耦</div>
                <div class="tech-sub">`file.direct` 路由 + Redis SETNX 幂等 + 3次重试死信兜底</div>
              </div>
            </div>

            <div class="tech-spec-item">
              <div class="tech-dot bg-emerald"></div>
              <div class="tech-content">
                <div class="tech-title">@Idempotent 接口防重提交</div>
                <div class="tech-sub">AOP 切面 + 参数哈希窗口期拦截 429 + 异常自动清除释放</div>
              </div>
            </div>

            <div class="tech-spec-item">
              <div class="tech-dot bg-blue"></div>
              <div class="tech-content">
                <div class="tech-title">Redisson 分布式锁防护</div>
                <div class="tech-sub">文件目录树互斥锁 Double-Check + 分片合并 waitTime=0 拒绝语义</div>
              </div>
            </div>

            <div class="tech-spec-item">
              <div class="tech-dot bg-amber"></div>
              <div class="tech-content">
                <div class="tech-title">高并发缓存防护三件套</div>
                <div class="tech-sub">`__NULL__` 空值防穿透 + 互斥锁防击穿 + 60s 随机抖动防雪崩</div>
              </div>
            </div>
          </div>
        </el-card>

        <!-- 快速开始指引 -->
        <el-card shadow="never" class="section-card" style="margin-top: 20px">
          <template #header>
            <div class="section-header">
              <div class="section-title">
                <el-icon><Compass /></el-icon>
                <span>三步极速上手</span>
              </div>
            </div>
          </template>

          <div class="quick-steps">
            <div class="step-item">
              <div class="step-num">1</div>
              <div class="step-text">
                <strong>创建代码仓库</strong>
                <p>在仓库模块点击新建，设定名称与公开/私有访问权限。</p>
              </div>
            </div>

            <div class="step-item">
              <div class="step-num">2</div>
              <div class="step-text">
                <strong>上传源代码或大文件</strong>
                <p>支持 1MB 分片断点续传，后台自动异步触发 RAG 向量切分。</p>
              </div>
            </div>

            <div class="step-item">
              <div class="step-num">3</div>
              <div class="step-text">
                <strong>唤起 AI 助手智能解析</strong>
                <p>在仓库详情页打开 AI 面板，体验流式代码讲解与精准引用。</p>
              </div>
            </div>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* 欢迎横幅 */
.welcome-banner {
  background: linear-gradient(135deg, #ffffff 0%, #f1f5f9 100%);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: 28px 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: var(--shadow-sm);
}

.greeting-title {
  font-size: 24px;
  font-weight: 800;
  color: var(--text-main);
  margin-bottom: 6px;
  letter-spacing: -0.5px;
}

.greeting-sub {
  font-size: 14px;
  color: var(--text-secondary);
  max-width: 680px;
  line-height: 1.5;
}

.welcome-actions {
  display: flex;
  gap: 12px;
}

.action-btn {
  background: var(--primary-gradient) !important;
  border: none !important;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.25) !important;
}

.action-btn-secondary {
  background: var(--bg-surface) !important;
  border: 1px solid var(--border-color) !important;
  color: var(--text-main) !important;
}

.action-btn-secondary:hover {
  border-color: var(--primary) !important;
  color: var(--primary) !important;
}

/* 4 个指标卡片网格 */
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.metric-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: var(--shadow-xs);
  transition: all var(--transition-normal);
}

.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
  border-color: #cbd5e1;
}

.metric-icon-box {
  width: 52px;
  height: 52px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.icon-purple {
  background: #eef2ff;
  color: #4f46e5;
}

.icon-blue {
  background: #eff6ff;
  color: #3b82f6;
}

.icon-emerald {
  background: #ecfdf5;
  color: #10b981;
}

.icon-amber {
  background: #fffbeb;
  color: #f59e0b;
}

.metric-info {
  display: flex;
  flex-direction: column;
}

.metric-label {
  font-size: 13px;
  color: var(--text-muted);
  font-weight: 500;
}

.metric-value {
  font-size: 24px;
  font-weight: 800;
  color: var(--text-main);
  line-height: 1.2;
  margin: 2px 0;
}

.metric-trend {
  font-size: 11px;
  font-weight: 600;
}

.text-primary { color: #4f46e5; }
.text-info { color: #0284c7; }
.text-success { color: #059669; }
.text-warning { color: #d97706; }

/* 内容区域 */
.content-grid {
  display: grid;
  grid-template-columns: 1.3fr 1fr;
  gap: 20px;
}

.section-card {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  background: var(--bg-surface);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
}

.active-badge {
  font-size: 11px;
  font-weight: 700;
  color: #059669;
  background: #ecfdf5;
  padding: 2px 8px;
  border-radius: var(--radius-full);
}

/* 仓库列表 */
.repo-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.repo-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-light);
  background: #fafbfc;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.repo-item:hover {
  background: var(--bg-surface);
  border-color: #c7d2fe;
  transform: translateX(4px);
  box-shadow: var(--shadow-sm);
}

.repo-main {
  flex: 1;
}

.repo-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.repo-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-main);
}

.vis-pill {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 4px;
}

.vis-public {
  background: #eff6ff;
  color: #2563eb;
}

.vis-private {
  background: #fef3c7;
  color: #d97706;
}

.repo-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 4px 0 8px;
  line-height: 1.4;
}

.repo-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 12px;
  color: var(--text-muted);
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.repo-arrow {
  color: var(--text-muted);
  font-size: 14px;
}

/* 空状态 */
.empty-state {
  padding: 40px 20px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty-icon {
  font-size: 48px;
  color: var(--text-muted);
  margin-bottom: 12px;
}

.empty-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
  margin-bottom: 4px;
}

.empty-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 16px;
}

/* 技术规格亮点 */
.tech-specs-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.tech-spec-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 10px 12px;
  background: #f8fafc;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-light);
}

.tech-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 6px;
  flex-shrink: 0;
}

.bg-indigo { background: #4f46e5; }
.bg-emerald { background: #10b981; }
.bg-blue { background: #3b82f6; }
.bg-amber { background: #f59e0b; }

.tech-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-main);
}

.tech-sub {
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 2px;
}

/* 快速步骤 */
.quick-steps {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.step-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.step-num {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--primary-light);
  color: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.step-text {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.4;
}

.step-text strong {
  color: var(--text-main);
  font-size: 13px;
}

.step-text p {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--text-muted);
}

@media (max-width: 1080px) {
  .metrics-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .content-grid {
    grid-template-columns: 1fr;
  }
  .welcome-banner {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }
}
</style>
