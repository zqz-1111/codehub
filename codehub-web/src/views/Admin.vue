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
  // 趋势图
  if (trendChartRef.value) {
    const chart = echarts.init(trendChartRef.value)
    const dailyData = await getDailyStats(7)
    chart.setOption({
      title: { text: '近7日趋势', left: 'center' },
      tooltip: { trigger: 'axis' },
      legend: { data: ['新用户', '新仓库', 'AI对话', '新文件'], bottom: 0 },
      xAxis: { type: 'category', data: dailyData.map((d: any) => d.date.substring(5)) },
      yAxis: { type: 'value' },
      series: [
        { name: '新用户', type: 'line', smooth: true, data: dailyData.map((d: any) => d.users) },
        { name: '新仓库', type: 'line', smooth: true, data: dailyData.map((d: any) => d.repos) },
        { name: 'AI对话', type: 'line', smooth: true, data: dailyData.map((d: any) => d.chats) },
        { name: '新文件', type: 'line', smooth: true, data: dailyData.map((d: any) => d.files) },
      ],
    })
  }

  // 饼图
  if (pieChartRef.value) {
    const chart = echarts.init(pieChartRef.value)
    chart.setOption({
      title: { text: '数据分布', left: 'center' },
      tooltip: { trigger: 'item' },
      series: [{
        type: 'pie',
        radius: '60%',
        data: [
          { value: stats.value.totalUsers, name: '用户' },
          { value: stats.value.totalRepos, name: '仓库' },
          { value: stats.value.totalFiles, name: '文件' },
          { value: stats.value.totalChats, name: 'AI对话' },
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
  ElMessage.success('角色已更新')
  loadUsers()
}

async function handleStatusChange(user: AdminUser, status: string) {
  await updateUserStatus(user.id, status)
  ElMessage.success('状态已更新')
  loadUsers()
}

async function handleResetPassword(user: AdminUser) {
  try {
    const { value } = await ElMessageBox.prompt('请输入新密码', '重置密码', { inputType: 'password' })
    if (value) {
      await resetPassword(user.id, value)
      ElMessage.success('密码已重置')
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
    await ElMessageBox.confirm(`确定强制删除仓库「${repo.name}」？`, '危险操作', { type: 'error' })
    await forceDeleteRepo(repo.id)
    ElMessage.success('已删除')
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
  modelForm.value = { provider: '', modelName: '', baseUrl: '', apiKey: '', enabled: true }
  showModelDialog.value = true
}

function openEditModel(model: ModelConfig) {
  editingModel.value = model
  modelForm.value = { ...model }
  showModelDialog.value = true
}

async function handleSaveModel() {
  if (editingModel.value) {
    await updateModelConfig(editingModel.value.id, modelForm.value)
  } else {
    await createModelConfig(modelForm.value)
  }
  ElMessage.success('保存成功')
  showModelDialog.value = false
  loadModels()
}

async function handleDeleteModel(model: ModelConfig) {
  try {
    await ElMessageBox.confirm('确定删除此模型配置？', '确认')
    await deleteModelConfig(model.id)
    ElMessage.success('已删除')
    loadModels()
  } catch {}
}

async function handleToggleModel(model: ModelConfig) {
  await toggleModelEnabled(model.id, !model.enabled)
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

// ========== 初始化 ==========
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
  <div>
    <h2 style="margin-bottom: 20px">管理后台</h2>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <!-- 统计概览 -->
      <el-tab-pane label="统计概览" name="stats">
        <el-row :gutter="20" style="margin-bottom: 20px">
          <el-col :span="6">
            <el-card shadow="hover">
              <div class="stat-card">
                <div class="stat-value">{{ stats.totalUsers || 0 }}</div>
                <div class="stat-label">总用户数</div>
                <div class="stat-today">今日 +{{ stats.todayNewUsers || 0 }}</div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover">
              <div class="stat-card">
                <div class="stat-value">{{ stats.totalRepos || 0 }}</div>
                <div class="stat-label">总仓库数</div>
                <div class="stat-today">今日 +{{ stats.todayNewRepos || 0 }}</div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover">
              <div class="stat-card">
                <div class="stat-value">{{ stats.totalFiles || 0 }}</div>
                <div class="stat-label">总文件数</div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover">
              <div class="stat-card">
                <div class="stat-value">{{ stats.totalChats || 0 }}</div>
                <div class="stat-label">AI对话次数</div>
                <div class="stat-today">今日 +{{ stats.todayNewChats || 0 }}</div>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <!-- ECharts图表 -->
        <el-row :gutter="20">
          <el-col :span="16">
            <el-card shadow="hover">
              <div ref="trendChartRef" style="height: 350px"></div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="hover">
              <div ref="pieChartRef" style="height: 350px"></div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- 用户管理 -->
      <el-tab-pane label="用户管理" name="users">
        <div style="margin-bottom: 16px; display: flex; gap: 12px">
          <el-input v-model="userKeyword" placeholder="搜索用户名/邮箱" style="width: 300px" @keyup.enter="loadUsers" />
          <el-button type="primary" @click="loadUsers">搜索</el-button>
        </div>
        <el-table :data="users" stripe>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="email" label="邮箱" />
          <el-table-column label="角色" width="120">
            <template #default="{ row }">
              <el-select :model-value="row.role" size="small" @change="(v: string) => handleRoleChange(row, v)">
                <el-option label="USER" value="USER" />
                <el-option label="ADMIN" value="ADMIN" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-select :model-value="row.status" size="small" @change="(v: string) => handleStatusChange(row, v)">
                <el-option label="ACTIVE" value="ACTIVE" />
                <el-option label="BANNED" value="BANNED" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="warning" size="small" @click="handleResetPassword(row)">重置密码</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination style="margin-top: 16px; text-align: center"
          v-model:current-page="userPage" :page-size="10" :total="userTotal"
          layout="prev, pager, next" @current-change="loadUsers" />
      </el-tab-pane>

      <!-- 仓库管理 -->
      <el-tab-pane label="仓库管理" name="repos">
        <el-table :data="repos" stripe>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="name" label="仓库名" />
          <el-table-column prop="ownerId" label="所有者ID" width="100" />
          <el-table-column label="可见性" width="120">
            <template #default="{ row }">
              <el-select :model-value="row.visibility" size="small" @change="(v: string) => handleVisibilityChange(row, v)">
                <el-option label="PUBLIC" value="PUBLIC" />
                <el-option label="PRIVATE" value="PRIVATE" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="180">
            <template #default="{ row }">{{ row.createdAt?.replace('T', ' ').substring(0, 19) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link type="danger" size="small" @click="handleForceDelete(row)">强制删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination style="margin-top: 16px; text-align: center"
          v-model:current-page="repoPage" :page-size="10" :total="repoTotal"
          layout="prev, pager, next" @current-change="loadRepos" />
      </el-tab-pane>

      <!-- 模型配置 -->
      <el-tab-pane label="模型配置" name="models">
        <div style="margin-bottom: 16px">
          <el-button type="primary" @click="openCreateModel">添加模型</el-button>
        </div>
        <el-table :data="models" stripe>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="provider" label="供应商" width="100" />
          <el-table-column prop="modelName" label="模型名称" />
          <el-table-column prop="baseUrl" label="Base URL" show-overflow-tooltip />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                {{ row.enabled ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openEditModel(row)">编辑</el-button>
              <el-button link size="small" @click="handleToggleModel(row)">
                {{ row.enabled ? '禁用' : '启用' }}
              </el-button>
              <el-button link type="danger" size="small" @click="handleDeleteModel(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination style="margin-top: 16px; text-align: center"
          v-model:current-page="modelPage" :page-size="10" :total="modelTotal"
          layout="prev, pager, next" @current-change="loadModels" />
      </el-tab-pane>

      <!-- 审计日志 -->
      <el-tab-pane label="审计日志" name="logs">
        <div style="margin-bottom: 16px; display: flex; gap: 12px">
          <el-select v-model="logActionFilter" placeholder="筛选操作类型" clearable style="width: 200px" @change="loadLogs">
            <el-option label="全部" value="" />
            <el-option label="修改角色" value="UPDATE_ROLE" />
            <el-option label="修改状态" value="UPDATE_STATUS" />
            <el-option label="重置密码" value="RESET_PASSWORD" />
            <el-option label="强制删除仓库" value="FORCE_DELETE_REPO" />
            <el-option label="创建模型" value="CREATE_MODEL" />
            <el-option label="删除模型" value="DELETE_MODEL" />
          </el-select>
        </div>
        <el-table :data="logs" stripe>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="userId" label="操作人ID" width="90" />
          <el-table-column prop="action" label="操作类型" width="150" />
          <el-table-column prop="targetType" label="目标类型" width="90" />
          <el-table-column prop="targetId" label="目标ID" width="80" />
          <el-table-column prop="detail" label="详情" show-overflow-tooltip />
          <el-table-column prop="ip" label="IP" width="130" />
          <el-table-column label="时间" width="180">
            <template #default="{ row }">{{ row.createdAt?.replace('T', ' ').substring(0, 19) }}</template>
          </el-table-column>
        </el-table>
        <el-pagination style="margin-top: 16px; text-align: center"
          v-model:current-page="logPage" :page-size="20" :total="logTotal"
          layout="prev, pager, next" @current-change="loadLogs" />
      </el-tab-pane>
    </el-tabs>

    <!-- 模型配置弹窗 -->
    <el-dialog v-model="showModelDialog" :title="editingModel ? '编辑模型' : '添加模型'" width="500px">
      <el-form :model="modelForm" label-width="100px">
        <el-form-item label="供应商" required>
          <el-input v-model="modelForm.provider" placeholder="ALIYUN / DEEPSEEK / XIAOMI" />
        </el-form-item>
        <el-form-item label="模型名称" required>
          <el-input v-model="modelForm.modelName" placeholder="qwen3.7-plus" />
        </el-form-item>
        <el-form-item label="Base URL">
          <el-input v-model="modelForm.baseUrl" placeholder="https://dashscope.aliyuncs.com/compatible-mode/v1" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="modelForm.apiKey" type="password" show-password placeholder="sk-..." />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="modelForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showModelDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveModel">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.stat-card {
  text-align: center;
  padding: 10px 0;
}
.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #409eff;
}
.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}
.stat-today {
  font-size: 12px;
  color: #67c23a;
  margin-top: 4px;
}
</style>
