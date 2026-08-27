<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import {
  listUsers, updateUserRole, updateUserStatus, resetPassword,
  listAllRepos, forceDeleteRepo, updateRepoVisibility,
  listModelConfigs, createModelConfig, updateModelConfig, deleteModelConfig, toggleModelEnabled,
  listAuditLogs,
  getDashboardStats, getDailyStats,
} from '../api/admin'
import type { AdminUser, ModelConfig, ModelConfigDTO, AuditLog } from '../api/admin'

const activeTab = ref('stats')

// ========== 统计数据 ==========
const stats = ref<Record<string, number>>({})
const trendChartRef = ref<HTMLElement | null>(null)
const pieChartRef = ref<HTMLElement | null>(null)

async function loadStats() {
  stats.value = await getDashboardStats()
  await nextTick()
  renderCharts()
}

async function renderCharts() {
  if (trendChartRef.value) {
    const chart = echarts.init(trendChartRef.value)
    const dailyData = await getDailyStats(7)
    chart.setOption({
      tooltip: {
        trigger: 'axis',
        backgroundColor: '#ffffff',
        borderColor: '#e2e8f0',
        textStyle: { color: '#0f172a' }
      },
      legend: { data: ['新用户', '新仓库', 'AI问答', '新文件'], bottom: 0 },
      grid: { left: '3%', right: '4%', bottom: '10%', top: '6%', containLabel: true },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: dailyData.map((d: any) => d.date.substring(5)),
        axisLine: { lineStyle: { color: '#cbd5e1' } },
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#f1f5f9' } }
      },
      series: [
        { name: '新用户', type: 'line', smooth: true, data: dailyData.map((d: any) => d.users), itemStyle: { color: '#4f46e5' } },
        { name: '新仓库', type: 'line', smooth: true, data: dailyData.map((d: any) => d.repos), itemStyle: { color: '#3b82f6' } },
        { name: 'AI问答', type: 'line', smooth: true, data: dailyData.map((d: any) => d.chats), itemStyle: { color: '#10b981' } },
        { name: '新文件', type: 'line', smooth: true, data: dailyData.map((d: any) => d.files), itemStyle: { color: '#f59e0b' } },
      ],
    })
  }

  if (pieChartRef.value) {
    const chart = echarts.init(pieChartRef.value)
    chart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [{
        type: 'pie',
        radius: ['45%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 6,
          borderColor: '#fff',
          borderWidth: 2
        },
        data: [
          { value: stats.value.totalUsers || 0, name: '用户', itemStyle: { color: '#4f46e5' } },
          { value: stats.value.totalRepos || 0, name: '仓库', itemStyle: { color: '#3b82f6' } },
          { value: stats.value.totalFiles || 0, name: '文件', itemStyle: { color: '#10b981' } },
          { value: stats.value.totalChats || 0, name: 'AI问答', itemStyle: { color: '#f59e0b' } },
        ],
      }],
    })
  }
}

// ========== 用户管理 ==========
const users = ref<AdminUser[]>([])
const userTotal = ref(0)
const userPage = ref(1)
const userKeyword = ref('')

async function loadUsers() {
  const res = await listUsers(userPage.value, 10, userKeyword.value || undefined)
  users.value = res.records
  userTotal.value = res.total
}

async function handleRoleChange(user: AdminUser, role: string) {
  await updateUserRole(user.id, role)
  ElMessage.success('用户权限角色已更新')
  loadUsers()
}

async function handleStatusChange(user: AdminUser, status: string) {
  await updateUserStatus(user.id, status)
  ElMessage.success('账号状态已变更')
  loadUsers()
}

async function handleResetPassword(user: AdminUser) {
  try {
    const { value } = await ElMessageBox.prompt('请输入新密码', '重置用户密码', {
      inputType: 'password',
      confirmButtonText: '确定重置',
      cancelButtonText: '取消'
    })
    if (value) {
      await resetPassword(user.id, value)
      ElMessage.success('密码已重置成功')
    }
  } catch {}
}

// ========== 仓库管理 ==========
const repos = ref<any[]>([])
const repoTotal = ref(0)
const repoPage = ref(1)

async function loadRepos() {
  const res = await listAllRepos(repoPage.value, 10)
  repos.value = res.records
  repoTotal.value = res.total
}

async function handleForceDelete(repo: any) {
  try {
    await ElMessageBox.confirm(`确定强制删除仓库「${repo.name}」？此操作级联清理文件与索引。`, '高危操作确认', {
      type: 'error',
      confirmButtonText: '强制删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger'
    })
    await forceDeleteRepo(repo.id)
    ElMessage.success('仓库已彻底删除')
    loadRepos()
  } catch {}
}

async function handleVisibilityChange(repo: any, visibility: string) {
  await updateRepoVisibility(repo.id, visibility)
  ElMessage.success('可见性已更新')
  loadRepos()
}

// ========== 模型配置 ==========
const models = ref<ModelConfig[]>([])
const modelTotal = ref(0)
const modelPage = ref(1)
const showModelDialog = ref(false)
const editingModel = ref<ModelConfig | null>(null)
const modelForm = ref<ModelConfigDTO>({ provider: '', modelName: '', baseUrl: '', apiKey: '', enabled: true })

async function loadModels() {
  const res = await listModelConfigs(modelPage.value, 10)
  models.value = res.records
  modelTotal.value = res.total
}

function openCreateModel() {
  editingModel.value = null
  modelForm.value = { provider: 'ALIYUN', modelName: 'qwen3.7-plus', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', apiKey: '', enabled: true }
  showModelDialog.value = true
}

function openEditModel(model: ModelConfig) {
  editingModel.value = model
  modelForm.value = { ...model }
  showModelDialog.value = true
}

async function handleSaveModel() {
  if (!modelForm.value.provider || !modelForm.value.modelName) {
    ElMessage.warning('请填写供应商与模型名称')
    return
  }
  if (editingModel.value) {
    await updateModelConfig(editingModel.value.id, modelForm.value)
  } else {
    await createModelConfig(modelForm.value)
  }
  ElMessage.success('模型配置保存成功')
  showModelDialog.value = false
  loadModels()
}

async function handleDeleteModel(model: ModelConfig) {
  try {
    await ElMessageBox.confirm('确定删除此模型配置？', '删除确认', { type: 'warning' })
    await deleteModelConfig(model.id)
    ElMessage.success('模型配置已删除')
    loadModels()
  } catch {}
}

async function handleToggleModel(model: ModelConfig) {
  await toggleModelEnabled(model.id, !model.enabled)
  ElMessage.success(model.enabled ? '已禁用' : '已启用')
  loadModels()
}

// ========== 审计日志 ==========
const logs = ref<AuditLog[]>([])
const logTotal = ref(0)
const logPage = ref(1)
const logActionFilter = ref('')

async function loadLogs() {
  const res = await listAuditLogs(logPage.value, 20, logActionFilter.value || undefined)
  logs.value = res.records
  logTotal.value = res.total
}

function handleTabChange(tab: string) {
  if (tab === 'stats') loadStats()
  else if (tab === 'users') loadUsers()
  else if (tab === 'repos') loadRepos()
  else if (tab === 'models') loadModels()
  else if (tab === 'logs') loadLogs()
}

onMounted(() => loadStats())
</script>

<template>
  <div class="admin-page animate-fade-in">
    <!-- 头部横幅 -->
    <div class="admin-header">
      <div>
        <h1 class="page-title">系统治理控制台</h1>
        <p class="page-desc">全站数据指标监控、多租户用户管控、大模型供应商调度与安全审计日志</p>
      </div>
    </div>

    <!-- 标签选项卡 -->
    <div class="admin-card">
      <el-tabs v-model="activeTab" class="custom-admin-tabs" @tab-change="handleTabChange">
        <!-- 统计概览 -->
        <el-tab-pane label="数据统计概览" name="stats">
          <div class="stats-overview">
            <div class="stat-cards-grid">
              <div class="stat-card">
                <div class="stat-icon icon-purple"><el-icon><User /></el-icon></div>
                <div class="stat-body">
                  <div class="stat-label">总注册用户</div>
                  <div class="stat-value">{{ stats.totalUsers || 0 }}</div>
                  <div class="stat-badge badge-green">今日 +{{ stats.todayNewUsers || 0 }}</div>
                </div>
              </div>

              <div class="stat-card">
                <div class="stat-icon icon-blue"><el-icon><Folder /></el-icon></div>
                <div class="stat-body">
                  <div class="stat-label">全站代码仓库</div>
                  <div class="stat-value">{{ stats.totalRepos || 0 }}</div>
                  <div class="stat-badge badge-green">今日 +{{ stats.todayNewRepos || 0 }}</div>
                </div>
              </div>

              <div class="stat-card">
                <div class="stat-icon icon-emerald"><el-icon><Document /></el-icon></div>
                <div class="stat-body">
                  <div class="stat-label">代码文件总数</div>
                  <div class="stat-value">{{ stats.totalFiles || 0 }}</div>
                  <div class="stat-badge badge-blue">MinIO 分布式存储</div>
                </div>
              </div>

              <div class="stat-card">
                <div class="stat-icon icon-amber"><el-icon><ChatDotRound /></el-icon></div>
                <div class="stat-body">
                  <div class="stat-label">AI 对话调用次数</div>
                  <div class="stat-value">{{ stats.totalChats || 0 }}</div>
                  <div class="stat-badge badge-green">今日 +{{ stats.todayNewChats || 0 }}</div>
                </div>
              </div>
            </div>

            <!-- ECharts 图表展示 -->
            <div class="charts-grid">
              <div class="chart-panel">
                <div class="chart-header">
                  <span class="chart-title">近 7 日全站业务指标趋势</span>
                </div>
                <div ref="trendChartRef" class="chart-container"></div>
              </div>

              <div class="chart-panel">
                <div class="chart-header">
                  <span class="chart-title">全站核心资产结构分布</span>
                </div>
                <div ref="pieChartRef" class="chart-container"></div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 用户管理 -->
        <el-tab-pane label="用户治理" name="users">
          <div class="pane-content">
            <div class="table-filter-bar">
              <el-input
                v-model="userKeyword"
                placeholder="搜索用户名或邮箱..."
                prefix-icon="Search"
                style="width: 320px"
                clearable
                @keyup.enter="loadUsers"
              />
              <el-button type="primary" @click="loadUsers">检索用户</el-button>
            </div>

            <el-table :data="users" stripe class="admin-table">
              <el-table-column prop="id" label="ID" width="70" align="center" />
              <el-table-column prop="username" label="用户名" min-width="140">
                <template #default="{ row }">
                  <div class="user-name-cell">
                    <div class="table-avatar">{{ (row.username || 'U').charAt(0).toUpperCase() }}</div>
                    <span class="font-bold">{{ row.username }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="email" label="邮箱" min-width="180">
                <template #default="{ row }">
                  <span>{{ row.email || '未绑定邮箱' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="系统角色" width="140" align="center">
                <template #default="{ row }">
                  <el-select
                    :model-value="row.role"
                    size="small"
                    @change="(v: string) => handleRoleChange(row, v)"
                  >
                    <el-option label="普通用户 USER" value="USER" />
                    <el-option label="管理员 ADMIN" value="ADMIN" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="账号状态" width="130" align="center">
                <template #default="{ row }">
                  <el-select
                    :model-value="row.status"
                    size="small"
                    @change="(v: string) => handleStatusChange(row, v)"
                  >
                    <el-option label="正常 ACTIVE" value="ACTIVE" />
                    <el-option label="封禁 BANNED" value="BANNED" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120" align="center">
                <template #default="{ row }">
                  <el-button link type="warning" size="small" @click="handleResetPassword(row)">
                    重置密码
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <div class="table-pagination">
              <el-pagination
                v-model:current-page="userPage"
                :page-size="10"
                :total="userTotal"
                layout="total, prev, pager, next"
                background
                @current-change="loadUsers"
              />
            </div>
          </div>
        </el-tab-pane>

        <!-- 仓库管理 -->
        <el-tab-pane label="全量仓库管控" name="repos">
          <div class="pane-content">
            <el-table :data="repos" stripe class="admin-table">
              <el-table-column prop="id" label="ID" width="70" align="center" />
              <el-table-column prop="name" label="仓库名称" min-width="160">
                <template #default="{ row }">
                  <span class="font-bold">{{ row.name }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="ownerId" label="拥有者 ID" width="100" align="center" />
              <el-table-column label="可见性控制" width="140" align="center">
                <template #default="{ row }">
                  <el-select
                    :model-value="row.visibility"
                    size="small"
                    @change="(v: string) => handleVisibilityChange(row, v)"
                  >
                    <el-option label="PUBLIC 公开" value="PUBLIC" />
                    <el-option label="PRIVATE 私有" value="PRIVATE" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="创建时间" width="180" align="center">
                <template #default="{ row }">
                  <span>{{ row.createdAt?.replace('T', ' ').substring(0, 19) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120" align="center">
                <template #default="{ row }">
                  <el-button link type="danger" size="small" @click="handleForceDelete(row)">
                    强制删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <div class="table-pagination">
              <el-pagination
                v-model:current-page="repoPage"
                :page-size="10"
                :total="repoTotal"
                layout="total, prev, pager, next"
                background
                @current-change="loadRepos"
              />
            </div>
          </div>
        </el-tab-pane>

        <!-- 模型配置 -->
        <el-tab-pane label="大模型调度配置" name="models">
          <div class="pane-content">
            <div class="table-filter-bar">
              <el-button type="primary" @click="openCreateModel">
                <el-icon><Plus /></el-icon>
                添加模型供应商
              </el-button>
            </div>

            <el-table :data="models" stripe class="admin-table">
              <el-table-column prop="id" label="ID" width="70" align="center" />
              <el-table-column prop="provider" label="供应商" width="130">
                <template #default="{ row }">
                  <span class="provider-badge">{{ row.provider }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="modelName" label="模型名称" min-width="160">
                <template #default="{ row }">
                  <span class="font-mono font-bold">{{ row.modelName }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="baseUrl" label="API Endpoint" min-width="240" show-overflow-tooltip />
              <el-table-column label="状态" width="100" align="center">
                <template #default="{ row }">
                  <span :class="['status-pill', row.enabled ? 'status-enabled' : 'status-disabled']">
                    {{ row.enabled ? '已启用' : '已禁用' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200" align="center">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="openEditModel(row)">编辑</el-button>
                  <el-button link size="small" @click="handleToggleModel(row)">
                    {{ row.enabled ? '禁用' : '启用' }}
                  </el-button>
                  <el-button link type="danger" size="small" @click="handleDeleteModel(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>

            <div class="table-pagination">
              <el-pagination
                v-model:current-page="modelPage"
                :page-size="10"
                :total="modelTotal"
                layout="total, prev, pager, next"
                background
                @current-change="loadModels"
              />
            </div>
          </div>
        </el-tab-pane>

        <!-- 审计日志 -->
        <el-tab-pane label="安全审计日志" name="logs">
          <div class="pane-content">
            <div class="table-filter-bar">
              <el-select
                v-model="logActionFilter"
                placeholder="按操作类型筛选..."
                clearable
                style="width: 220px"
                @change="loadLogs"
              >
                <el-option label="全部操作" value="" />
                <el-option label="修改角色 UPDATE_ROLE" value="UPDATE_ROLE" />
                <el-option label="修改状态 UPDATE_STATUS" value="UPDATE_STATUS" />
                <el-option label="重置密码 RESET_PASSWORD" value="RESET_PASSWORD" />
                <el-option label="强制删除仓库 FORCE_DELETE_REPO" value="FORCE_DELETE_REPO" />
                <el-option label="创建模型 CREATE_MODEL" value="CREATE_MODEL" />
                <el-option label="删除模型 DELETE_MODEL" value="DELETE_MODEL" />
              </el-select>
            </div>

            <el-table :data="logs" stripe class="admin-table">
              <el-table-column prop="id" label="ID" width="70" align="center" />
              <el-table-column prop="userId" label="操作人ID" width="90" align="center" />
              <el-table-column prop="action" label="操作事件" width="170">
                <template #default="{ row }">
                  <span class="action-tag">{{ row.action }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="detail" label="操作详细信息" min-width="220" show-overflow-tooltip />
              <el-table-column prop="ip" label="客户端 IP" width="140" />
              <el-table-column label="发生时间" width="180" align="center">
                <template #default="{ row }">
                  <span>{{ row.createdAt?.replace('T', ' ').substring(0, 19) }}</span>
                </template>
              </el-table-column>
            </el-table>

            <div class="table-pagination">
              <el-pagination
                v-model:current-page="logPage"
                :page-size="20"
                :total="logTotal"
                layout="total, prev, pager, next"
                background
                @current-change="loadLogs"
              />
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 模型配置弹窗 -->
    <el-dialog
      v-model="showModelDialog"
      :title="editingModel ? '编辑大模型配置' : '添加大模型供应商'"
      width="520px"
      destroy-on-close
    >
      <el-form :model="modelForm" label-position="top">
        <el-form-item label="供应商标识 (Provider)" required>
          <el-input v-model="modelForm.provider" placeholder="ALIYUN / DEEPSEEK / OPENAI" />
        </el-form-item>
        <el-form-item label="模型名称 (Model Name)" required>
          <el-input v-model="modelForm.modelName" placeholder="qwen3.7-plus" />
        </el-form-item>
        <el-form-item label="Base URL">
          <el-input v-model="modelForm.baseUrl" placeholder="https://dashscope.aliyuncs.com/compatible-mode/v1" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="modelForm.apiKey" type="password" show-password placeholder="sk-..." />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="modelForm.enabled" active-text="启用中" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showModelDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveModel">保存配置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-title {
  font-size: 22px;
  font-weight: 800;
  color: var(--text-main);
  letter-spacing: -0.5px;
}

.page-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.admin-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

/* 统计卡片 */
.stat-cards-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: #f8fafc;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 14px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}

.icon-purple { background: #eef2ff; color: #4f46e5; }
.icon-blue { background: #eff6ff; color: #3b82f6; }
.icon-emerald { background: #ecfdf5; color: #10b981; }
.icon-amber { background: #fffbeb; color: #f59e0b; }

.stat-body {
  display: flex;
  flex-direction: column;
}

.stat-label {
  font-size: 12px;
  color: var(--text-muted);
  font-weight: 500;
}

.stat-value {
  font-size: 22px;
  font-weight: 800;
  color: var(--text-main);
  line-height: 1.2;
}

.stat-badge {
  font-size: 11px;
  font-weight: 600;
  margin-top: 2px;
}

.badge-green { color: #059669; }
.badge-blue { color: #2563eb; }

/* 图表面板 */
.charts-grid {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 20px;
}

.chart-panel {
  background: #f8fafc;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 16px;
}

.chart-header {
  margin-bottom: 12px;
}

.chart-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-main);
}

.chart-container {
  height: 320px;
  width: 100%;
}

/* 标签页内容 */
.pane-content {
  padding: 10px 0;
}

.table-filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.admin-table {
  width: 100%;
}

.user-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.table-avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--primary-gradient);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
}

.font-bold {
  font-weight: 600;
  color: var(--text-main);
}

.provider-badge {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 6px;
  background: #e0e7ff;
  color: var(--primary);
  border-radius: 4px;
}

.status-pill {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: var(--radius-full);
}

.status-enabled {
  background: #ecfdf5;
  color: #059669;
}

.status-disabled {
  background: #f1f5f9;
  color: #94a3b8;
}

.action-tag {
  font-size: 11px;
  font-family: 'JetBrains Mono', monospace;
  color: var(--primary);
  background: var(--primary-light);
  padding: 2px 6px;
  border-radius: 4px;
}

.table-pagination {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

@media (max-width: 1100px) {
  .stat-cards-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .charts-grid {
    grid-template-columns: 1fr;
  }
}
</style>
