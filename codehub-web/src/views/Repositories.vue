<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createRepo, listMyRepos, listPublicRepos, deleteRepo } from '../api/repo'
import type { RepoVO, CreateRepoRequest } from '../api/repo'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

// 列表状态
const activeTab = ref<'my' | 'public'>('my')
const repos = ref<RepoVO[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(9)
const loading = ref(false)
const searchQuery = ref('')
const viewMode = ref<'grid' | 'table'>('grid')

// 创建弹窗状态
const showCreateDialog = ref(false)
const createLoading = ref(false)
const createForm = ref<CreateRepoRequest>({
  name: '',
  description: '',
  visibility: 'PRIVATE',
})

// 过滤后的仓库列表
const filteredRepos = computed(() => {
  if (!searchQuery.value.trim()) return repos.value
  const q = searchQuery.value.toLowerCase()
  return repos.value.filter(r =>
    r.name.toLowerCase().includes(q) ||
    (r.description && r.description.toLowerCase().includes(q))
  )
})

// 加载仓库列表
async function loadRepos() {
  loading.value = true
  try {
    const fn = activeTab.value === 'my' ? listMyRepos : listPublicRepos
    const res = await fn(currentPage.value, pageSize.value)
    repos.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

// 切换Tab
function handleTabChange(tab: 'my' | 'public') {
  activeTab.value = tab
  currentPage.value = 1
  searchQuery.value = ''
  loadRepos()
}

// 翻页
function handlePageChange(page: number) {
  currentPage.value = page
  loadRepos()
}

// 打开创建弹窗
function openCreateDialog() {
  createForm.value = { name: '', description: '', visibility: 'PRIVATE' }
  showCreateDialog.value = true
}

// 提交创建
async function handleCreate() {
  const name = createForm.value.name.trim()
  if (!name) {
    ElMessage.warning('请输入仓库名称')
    return
  }
  if (!/^[a-zA-Z0-9_-]+$/.test(name)) {
    ElMessage.warning('仓库名只允许字母、数字、横杠与下划线')
    return
  }

  createLoading.value = true
  try {
    const repo = await createRepo(createForm.value)
    ElMessage.success('仓库创建成功')
    showCreateDialog.value = false
    router.push(`/repos/${repo.id}`)
  } finally {
    createLoading.value = false
  }
}

// 删除仓库
async function handleDelete(repo: RepoVO, event?: Event) {
  if (event) event.stopPropagation()
  try {
    await ElMessageBox.confirm(
      `确定删除仓库「${repo.name}」？此操作将级联清理所有关联文件与索引，且不可恢复。`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger'
      }
    )
    await deleteRepo(repo.id)
    ElMessage.success('仓库已安全删除')
    loadRepos()
  } catch {
    // 用户取消
  }
}

// 跳转详情
function goDetail(id: number) {
  router.push(`/repos/${id}`)
}

onMounted(() => loadRepos())
</script>

<template>
  <div class="repos-page animate-fade-in">
    <!-- 头部工具栏 -->
    <div class="repos-header">
      <div class="header-titles">
        <h1 class="page-title">代码仓库管理</h1>
        <p class="page-desc">协同管理私有项目与全网开源资产，支持 RAG 向量索引与流式代码对话</p>
      </div>

      <el-button type="primary" size="large" class="create-btn" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>
        新建代码仓库
      </el-button>
    </div>

    <!-- 选项卡与过滤工具栏 -->
    <div class="toolbar-card">
      <div class="tabs-group">
        <button
          :class="['custom-tab-btn', activeTab === 'my' ? 'is-active' : '']"
          @click="handleTabChange('my')"
        >
          <el-icon><User /></el-icon>
          <span>我的仓库</span>
          <span class="tab-badge">{{ activeTab === 'my' ? total : '' }}</span>
        </button>
        <button
          :class="['custom-tab-btn', activeTab === 'public' ? 'is-active' : '']"
          @click="handleTabChange('public')"
        >
          <el-icon><Share /></el-icon>
          <span>公开开源仓库</span>
          <span class="tab-badge">{{ activeTab === 'public' ? total : '' }}</span>
        </button>
      </div>

      <div class="filter-group">
        <el-input
          v-model="searchQuery"
          placeholder="按名称或描述搜索仓库..."
          prefix-icon="Search"
          clearable
          class="search-input"
        />

        <div class="view-toggle">
          <button
            :class="['toggle-btn', viewMode === 'grid' ? 'active' : '']"
            @click="viewMode = 'grid'"
            title="卡片视图"
          >
            <el-icon><Menu /></el-icon>
          </button>
          <button
            :class="['toggle-btn', viewMode === 'table' ? 'active' : '']"
            @click="viewMode = 'table'"
            title="列表视图"
          >
            <el-icon><Tickets /></el-icon>
          </button>
        </div>
      </div>
    </div>

    <!-- 加载中骨架屏 -->
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="6" animated />
    </div>

    <!-- 空数据状态 -->
    <div v-else-if="filteredRepos.length === 0" class="empty-box">
      <div class="empty-illu">
        <el-icon><FolderAdd /></el-icon>
      </div>
      <div class="empty-title">未检索到代码仓库</div>
      <p class="empty-subtitle">您可以立即创建一个新仓库或调整搜索关键字</p>
      <el-button type="primary" @click="openCreateDialog">立即创建仓库</el-button>
    </div>

    <!-- 卡片网格视图 -->
    <div v-else-if="viewMode === 'grid'" class="repos-grid">
      <div
        v-for="repo in filteredRepos"
        :key="repo.id"
        class="repo-card"
        @click="goDetail(repo.id)"
      >
        <div class="card-top">
          <div class="repo-icon">
            <el-icon><Folder /></el-icon>
          </div>
          <div class="repo-badges">
            <span :class="['vis-tag', repo.visibility === 'PUBLIC' ? 'vis-public' : 'vis-private']">
              {{ repo.visibility === 'PUBLIC' ? 'Public' : 'Private' }}
            </span>
          </div>
        </div>

        <div class="card-body">
          <div class="repo-full-name">
            <span class="owner-prefix">{{ repo.ownerName }} /</span>
            <span class="main-name">{{ repo.name }}</span>
          </div>
          <p class="repo-description">
            {{ repo.description || '暂无详细描述信息' }}
          </p>
        </div>

        <div class="card-footer">
          <div class="footer-meta">
            <span class="branch-pill">
              <el-icon><Branch /></el-icon>
              {{ repo.defaultBranch || 'main' }}
            </span>
            <span class="star-count">
              <el-icon><Star /></el-icon>
              {{ repo.starCount || 0 }}
            </span>
          </div>

          <div class="footer-actions">
            <el-button
              link
              type="primary"
              size="small"
              @click.stop="goDetail(repo.id)"
            >
              浏览
            </el-button>
            <el-button
              v-if="repo.ownerId === userStore.user?.id || userStore.isAdmin()"
              link
              type="danger"
              size="small"
              @click.stop="handleDelete(repo, $event)"
            >
              删除
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 表格列表视图 -->
    <div v-else class="table-container">
      <el-table :data="filteredRepos" stripe class="custom-table" @row-click="(row: RepoVO) => goDetail(row.id)">
        <el-table-column label="仓库名称" min-width="220">
          <template #default="{ row }">
            <div class="table-repo-cell">
              <div class="table-repo-icon"><el-icon><Folder /></el-icon></div>
              <div>
                <div class="table-repo-name">{{ row.ownerName }} / {{ row.name }}</div>
                <div class="table-repo-desc">{{ row.description || '无描述' }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="可见性" width="120" align="center">
          <template #default="{ row }">
            <span :class="['vis-tag', row.visibility === 'PUBLIC' ? 'vis-public' : 'vis-private']">
              {{ row.visibility }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="默认分支" width="120" align="center">
          <template #default="{ row }">
            <span class="branch-pill">{{ row.defaultBranch || 'main' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="Stars" width="90" align="center">
          <template #default="{ row }">
            <span class="star-count"><el-icon><Star /></el-icon> {{ row.starCount || 0 }}</span>
          </template>
        </el-table-column>

        <el-table-column label="更新时间" width="160" align="center">
          <template #default="{ row }">
            <span class="time-text">{{ (row.updatedAt || '').substring(0, 10) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="140" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="goDetail(row.id)">进入</el-button>
            <el-button
              v-if="row.ownerId === userStore.user?.id || userStore.isAdmin()"
              link
              type="danger"
              @click.stop="handleDelete(row, $event)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 底部翻页 -->
    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        background
        @current-change="handlePageChange"
      />
    </div>

    <!-- 新建仓库弹窗 -->
    <el-dialog v-model="showCreateDialog" title="新建代码仓库" width="520px" destroy-on-close>
      <el-form :model="createForm" label-position="top" class="create-modal-form">
        <el-form-item label="仓库名称 (Repository Name)" required>
          <el-input
            v-model="createForm.name"
            placeholder="例如：auth-service 或 ai-engine"
            maxlength="100"
            size="large"
          />
          <div class="field-hint">仓库名仅支持字母、数字、中划线 (-) 与下划线 (_)</div>
        </el-form-item>

        <el-form-item label="描述信息 (Description)">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            placeholder="简要说明此仓库的代码定位、架构或模块功能（选填）"
            maxlength="500"
          />
        </el-form-item>

        <el-form-item label="访问可见性 (Visibility)">
          <div class="vis-options">
            <div
              :class="['vis-card', createForm.visibility === 'PRIVATE' ? 'selected' : '']"
              @click="createForm.visibility = 'PRIVATE'"
            >
              <div class="vis-card-header">
                <el-icon><Lock /></el-icon>
                <strong>Private 私有仓库</strong>
              </div>
              <p>仅仓库拥有者与受邀协作成员可见与操作</p>
            </div>

            <div
              :class="['vis-card', createForm.visibility === 'PUBLIC' ? 'selected' : '']"
              @click="createForm.visibility = 'PUBLIC'"
            >
              <div class="vis-card-header">
                <el-icon><Unlock /></el-icon>
                <strong>Public 公开仓库</strong>
              </div>
              <p>全平台开发者均可查阅、克隆与发起 AI 问答</p>
            </div>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-actions">
          <el-button size="large" @click="showCreateDialog = false">取消</el-button>
          <el-button type="primary" size="large" :loading="createLoading" @click="handleCreate">
            立即创建仓库
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.repos-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 头部 */
.repos-header {
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

.create-btn {
  background: var(--primary-gradient) !important;
  border: none !important;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.25) !important;
}

/* 工具栏 */
.toolbar-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: 12px 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: var(--shadow-xs);
  gap: 16px;
}

.tabs-group {
  display: flex;
  gap: 8px;
  background: var(--bg-subtle);
  padding: 4px;
  border-radius: var(--radius-md);
}

.custom-tab-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  border: none;
  background: transparent;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.custom-tab-btn.is-active {
  background: var(--bg-surface);
  color: var(--primary);
  box-shadow: var(--shadow-xs);
}

.tab-badge {
  font-size: 11px;
  color: var(--text-muted);
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-input {
  width: 280px;
}

.view-toggle {
  display: flex;
  background: var(--bg-subtle);
  padding: 3px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-color);
}

.toggle-btn {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  border: none;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.toggle-btn.active {
  background: var(--bg-surface);
  color: var(--primary);
  box-shadow: var(--shadow-xs);
}

/* 卡片网格 */
.repos-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.repo-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: 20px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  cursor: pointer;
  box-shadow: var(--shadow-card);
  transition: all var(--transition-normal);
}

.repo-card:hover {
  transform: translateY(-3px);
  border-color: #c7d2fe;
  box-shadow: var(--shadow-card-hover);
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.repo-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  background: var(--primary-light);
  color: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.vis-tag {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: var(--radius-full);
}

.vis-public {
  background: #ecfdf5;
  color: #059669;
}

.vis-private {
  background: #fffbeb;
  color: #d97706;
}

.repo-full-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
  margin-bottom: 6px;
  line-height: 1.3;
}

.owner-prefix {
  color: var(--text-muted);
  font-weight: 500;
  margin-right: 4px;
}

.repo-description {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 38px;
}

.card-footer {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.footer-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.branch-pill {
  font-size: 11px;
  color: var(--text-secondary);
  background: var(--bg-subtle);
  padding: 2px 6px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.star-count {
  font-size: 12px;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 3px;
}

.footer-actions {
  display: flex;
  gap: 8px;
}

/* 列表表格 */
.table-container {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.table-repo-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.table-repo-icon {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: var(--primary-light);
  color: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
}

.table-repo-name {
  font-weight: 700;
  font-size: 14px;
  color: var(--text-main);
}

.table-repo-desc {
  font-size: 12px;
  color: var(--text-muted);
}

.time-text {
  font-size: 12px;
  color: var(--text-muted);
}

/* 翻页 */
.pagination-bar {
  display: flex;
  justify-content: center;
  margin-top: 10px;
}

/* 新建弹窗 */
.create-modal-form {
  padding: 4px 0;
}

.field-hint {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
}

.vis-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  width: 100%;
}

.vis-card {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 14px;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.vis-card:hover {
  border-color: var(--border-hover);
}

.vis-card.selected {
  border-color: var(--primary);
  background: var(--primary-light);
}

.vis-card-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-main);
  margin-bottom: 4px;
}

.vis-card p {
  font-size: 11px;
  color: var(--text-muted);
  line-height: 1.3;
  margin: 0;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 1100px) {
  .repos-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 700px) {
  .repos-grid {
    grid-template-columns: 1fr;
  }
  .toolbar-card {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
