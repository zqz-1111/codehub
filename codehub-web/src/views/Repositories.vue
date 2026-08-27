<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Star, Delete, View } from '@element-plus/icons-vue'
import { createRepo, listMyRepos, listPublicRepos, deleteRepo } from '../api/repo'
import type { RepoVO, CreateRepoRequest } from '../api/repo'

const router = useRouter()

// 列表状态
const activeTab = ref('my')
const repos = ref<RepoVO[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const loading = ref(false)

// 创建弹窗状态
const showCreateDialog = ref(false)
const createLoading = ref(false)
const createForm = ref<CreateRepoRequest>({
  name: '',
  description: '',
  visibility: 'PRIVATE',
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
function handleTabChange() {
  currentPage.value = 1
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
  if (!createForm.value.name.trim()) {
    ElMessage.warning('请输入仓库名')
    return
  }
  createLoading.value = true
  try {
    const repo = await createRepo(createForm.value)
    ElMessage.success('仓库创建成功')
    showCreateDialog.value = false
    // 跳转到仓库详情
    router.push(`/repos/${repo.id}`)
  } finally {
    createLoading.value = false
  }
}

// 删除仓库
async function handleDelete(repo: RepoVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除仓库「${repo.name}」？此操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await deleteRepo(repo.id)
    ElMessage.success('已删除')
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
  <div>
    <!-- 顶部操作栏 -->
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px">
      <h2 style="margin: 0">仓库管理</h2>
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>
        创建仓库
      </el-button>
    </div>

    <!-- Tab切换 -->
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="我的仓库" name="my" />
      <el-tab-pane label="Public仓库" name="public" />
    </el-tabs>

    <!-- 仓库列表 -->
    <el-table :data="repos" v-loading="loading" stripe style="width: 100%">
      <el-table-column label="仓库名" min-width="200">
        <template #default="{ row }">
          <el-link type="primary" @click="goDetail(row.id)">
            {{ row.ownerName }}/{{ row.name }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.description || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="可见性" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.visibility === 'PUBLIC' ? 'success' : 'info'" size="small">
            {{ row.visibility }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Stars" width="80" align="center">
        <template #default="{ row }">
          <span style="display: inline-flex; align-items: center; gap: 4px">
            <el-icon><Star /></el-icon>
            {{ row.starCount }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="180">
        <template #default="{ row }">
          {{ row.updatedAt?.replace('T', ' ').substring(0, 19) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row.id)">
            <el-icon><View /></el-icon>
            查看
          </el-button>
          <el-button link type="danger" @click="handleDelete(row)">
            <el-icon><Delete /></el-icon>
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div style="display: flex; justify-content: center; margin-top: 20px">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        @current-change="handlePageChange"
      />
    </div>

    <!-- 创建仓库弹窗 -->
    <el-dialog v-model="showCreateDialog" title="创建仓库" width="500px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="仓库名" required>
          <el-input
            v-model="createForm.name"
            placeholder="my-project（只允许字母、数字、横杠、下划线）"
            maxlength="100"
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            placeholder="仓库描述（可选）"
            maxlength="500"
          />
        </el-form-item>
        <el-form-item label="可见性">
          <el-radio-group v-model="createForm.visibility">
            <el-radio value="PRIVATE">Private — 仅成员可见</el-radio>
            <el-radio value="PUBLIC">Public — 所有人可见</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="handleCreate">
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
