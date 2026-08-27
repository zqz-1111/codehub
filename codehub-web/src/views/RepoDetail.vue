<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  Upload,
  Delete,
  Document,
  Folder,
  ChatDotRound,
  CopyDocument,
  Share
} from '@element-plus/icons-vue'
import { getRepo } from '../api/repo'
import { listFiles, getFileContent, uploadFile, deleteFile } from '../api/file'
import type { RepoVO } from '../api/repo'
import type { FileVO } from '../api/file'
import MonacoEditor from '../components/MonacoEditor.vue'
import ChunkUploader from '../components/ChunkUploader.vue'
import FileTreeNode from '../components/FileTreeNode.vue'
import type { TreeNode } from '../components/FileTreeNode.vue'
import AiPanel from '../components/AiPanel.vue'

const route = useRoute()
const router = useRouter()
const repoId = Number(route.params.id)

const repo = ref<RepoVO | null>(null)
const loading = ref(true)

// 文件列表
const files = ref<FileVO[]>([])
const currentFile = ref<FileVO | null>(null)
const fileContent = ref<string>('')
const fileLoading = ref(false)
const fileSearch = ref('')

// AI面板抽屉状态
const showAiPanel = ref(false)

// 从AI引用打开文件
async function openFileFromRef(fileId: number) {
  const file = files.value.find(f => f.id === fileId)
  if (file) {
    currentFile.value = file
    fileLoading.value = true
    try {
      fileContent.value = await getFileContent(repoId, fileId)
    } catch {
      fileContent.value = '// 文件内容加载失败'
    } finally {
      fileLoading.value = false
    }
  }
}

const expandedDirs = ref<Set<string>>(new Set())

// 构建文件树
const fileTree = computed<TreeNode[]>(() => {
  const root: TreeNode[] = []
  const dirMap = new Map<string, TreeNode>()

  let list = files.value
  if (fileSearch.value.trim()) {
    const q = fileSearch.value.toLowerCase()
    list = list.filter(f => f.path.toLowerCase().includes(q))
  }

  const sorted = [...list].sort((a, b) => {
    const aIsDir = a.path.includes('/') && !a.path.endsWith('/')
    const bIsDir = b.path.includes('/') && !b.path.endsWith('/')
    if (aIsDir !== bIsDir) return aIsDir ? -1 : 1
    return a.path.localeCompare(b.path)
  })

  for (const f of sorted) {
    const parts = f.path.split('/')
    let current = root
    let currentPath = ''

    for (let i = 0; i < parts.length; i++) {
      const part = parts[i]
      currentPath = currentPath ? currentPath + '/' + part : part

      if (i === parts.length - 1) {
        current.push({ name: part, path: f.path, type: 'file', file: f })
      } else {
        let dir = dirMap.get(currentPath)
        if (!dir) {
          dir = { name: part, path: currentPath, type: 'dir', children: [] }
          dirMap.set(currentPath, dir)
          current.push(dir)
          if (i === 0 || fileSearch.value) expandedDirs.value.add(currentPath)
        }
        current = dir.children!
      }
    }
  }
  return root
})

// 推断代码高亮语言
function getLanguage(filename: string): string {
  const ext = filename.split('.').pop()?.toLowerCase() || ''
  const langMap: Record<string, string> = {
    java: 'java', js: 'javascript', ts: 'typescript', tsx: 'typescript',
    jsx: 'javascript', py: 'python', go: 'go', rs: 'rust',
    html: 'html', css: 'css', scss: 'scss', less: 'less',
    json: 'json', xml: 'xml', yaml: 'yaml', yml: 'yaml',
    md: 'markdown', sql: 'sql', sh: 'shell', bash: 'shell',
    vue: 'html', txt: 'plaintext', properties: 'ini',
  }
  return langMap[ext] || 'plaintext'
}

// 加载仓库信息
async function loadRepo() {
  loading.value = true
  try {
    repo.value = await getRepo(repoId)
  } catch {
    ElMessage.error('仓库不存在或无权访问')
    router.push('/repositories')
  } finally {
    loading.value = false
  }
}

// 加载文件列表
async function loadFiles() {
  try {
    files.value = await listFiles(repoId)
    // 默认打开第一个文件
    if (files.value.length > 0 && !currentFile.value) {
      const firstFile = files.value[0]
      clickNode({ name: firstFile.path, path: firstFile.path, type: 'file', file: firstFile })
    }
  } catch {
    // 忽略
  }
}

// 点击文件或目录
async function clickNode(node: TreeNode) {
  if (node.type === 'dir') {
    if (expandedDirs.value.has(node.path)) {
      expandedDirs.value.delete(node.path)
    } else {
      expandedDirs.value.add(node.path)
    }
    return
  }

  if (!node.file) return
  currentFile.value = node.file as FileVO
  fileLoading.value = true
  try {
    fileContent.value = await getFileContent(repoId, node.file.id)
  } catch {
    fileContent.value = '// 文件内容读取失败'
  } finally {
    fileLoading.value = false
  }
}

// 标准单文件上传
async function handleUpload(uploadFile_: any) {
  const file = uploadFile_.file as File
  const path = file.name
  try {
    await uploadFile(repoId, file, path)
    ElMessage.success('文件上传成功，已触发异步索引构建')
    loadFiles()
  } catch {
    ElMessage.error('上传失败')
  }
}

// 删除文件
async function handleDeleteFile(file: { id: number; path: string }) {
  try {
    await ElMessageBox.confirm(`确定删除文件「${file.path}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger'
    })
    await deleteFile(repoId, file.id)
    ElMessage.success('文件已删除')
    if (currentFile.value?.id === file.id) {
      currentFile.value = null
      fileContent.value = ''
    }
    loadFiles()
  } catch {
    // 用户取消
  }
}

function copyCloneUrl() {
  const url = `http://localhost:8080/repos/${repoId}.git`
  navigator.clipboard.writeText(url)
  ElMessage.success('克隆地址已复制到剪贴板')
}

function formatSize(bytes: number | null): string {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

onMounted(() => {
  loadRepo()
  loadFiles()
})
</script>

<template>
  <div v-loading="loading" class="repo-detail-page animate-fade-in">
    <!-- 顶部仓库信息横幅 -->
    <div class="repo-banner">
      <div class="banner-left">
        <el-button class="back-btn" @click="router.push('/repositories')">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>

        <div class="repo-meta-block">
          <div class="repo-heading-row">
            <h1 class="repo-title">
              <span class="owner-name">{{ repo?.ownerName }} /</span>
              <span class="repo-name">{{ repo?.name }}</span>
            </h1>
            <span :class="['vis-tag', repo?.visibility === 'PUBLIC' ? 'vis-public' : 'vis-private']">
              {{ repo?.visibility }}
            </span>
            <span class="branch-tag">
              <el-icon><Share /></el-icon>
              {{ repo?.defaultBranch || 'main' }}
            </span>
          </div>

          <p class="repo-desc">{{ repo?.description || '暂无详细描述' }}</p>
        </div>
      </div>

      <div class="banner-right">
        <el-button class="clone-btn" @click="copyCloneUrl">
          <el-icon><CopyDocument /></el-icon>
          <span>克隆仓库</span>
        </el-button>

        <el-button
          type="primary"
          :class="['ai-toggle-btn', showAiPanel ? 'is-active' : '']"
          @click="showAiPanel = !showAiPanel"
        >
          <el-icon><ChatDotRound /></el-icon>
          <span>{{ showAiPanel ? '收起 AI 助手' : 'Ask AI' }}</span>
        </el-button>
      </div>
    </div>

    <!-- 工作区主体双栏 / 三栏布局 -->
    <div class="workspace-layout">
      <!-- 左侧：文件资源管理器 -->
      <div class="file-explorer-pane">
        <div class="explorer-header">
          <div class="explorer-title">
            <el-icon><Folder /></el-icon>
            <span>文件列表 ({{ files.length }})</span>
          </div>

          <div class="explorer-actions">
            <!-- 单文件上传 -->
            <el-upload
              :show-file-list="false"
              :http-request="handleUpload"
              accept="*"
            >
              <button class="upload-icon-btn" title="上传单个文件">
                <el-icon><Upload /></el-icon>
              </button>
            </el-upload>

            <!-- 1MB 切片大文件上传 -->
            <ChunkUploader :repo-id="repoId" @uploaded="loadFiles" />
          </div>
        </div>

        <!-- 文件快速检索 -->
        <div class="tree-search-bar">
          <el-input
            v-model="fileSearch"
            placeholder="过滤文件..."
            prefix-icon="Search"
            size="small"
            clearable
          />
        </div>

        <!-- 文件树组件 -->
        <div class="explorer-tree-box">
          <div v-if="fileTree.length === 0" class="tree-empty">
            <el-icon><Document /></el-icon>
            <span>暂无代码文件</span>
          </div>
          <FileTreeNode
            v-else
            :nodes="fileTree"
            :expanded-dirs="expandedDirs"
            :current-path="currentFile?.path || null"
            @click="clickNode"
            @delete="handleDeleteFile"
          />
        </div>
      </div>

      <!-- 中间：Monaco 代码编辑器容器 -->
      <div class="editor-pane" v-loading="fileLoading">
        <template v-if="currentFile">
          <!-- 编辑器顶部状态栏 -->
          <div class="editor-toolbar">
            <div class="file-breadcrumb">
              <el-icon><Document /></el-icon>
              <span class="file-path-text">{{ currentFile.path }}</span>
              <span class="file-size-chip">{{ formatSize(currentFile.sizeBytes) }}</span>
            </div>

            <div class="editor-actions">
              <span class="lang-indicator">{{ getLanguage(currentFile.path) }}</span>
              <button class="editor-btn" title="删除文件" @click="handleDeleteFile(currentFile)">
                <el-icon><Delete /></el-icon>
              </button>
            </div>
          </div>

          <!-- Monaco Editor 实例 -->
          <div class="monaco-wrapper">
            <MonacoEditor
              :code="fileContent"
              :language="getLanguage(currentFile.path)"
              :read-only="true"
            />
          </div>
        </template>

        <!-- 未选择文件空状态 -->
        <template v-else>
          <div class="editor-empty">
            <div class="empty-file-illu">
              <el-icon><Document /></el-icon>
            </div>
            <div class="empty-file-title">未选中文件</div>
            <p class="empty-file-sub">请在左侧文件浏览器中选择文件，或上传新代码</p>
          </div>
        </template>
      </div>

      <!-- 右侧：浮动 / 固定 AI 对话助手 -->
      <div v-if="showAiPanel" class="ai-assistant-pane animate-slide-up">
        <AiPanel
          :repo-id="repoId"
          @open-file="openFileFromRef"
          @close="showAiPanel = false"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.repo-detail-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: calc(100vh - 110px);
}

/* 顶部横幅 */
.repo-banner {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: 16px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: var(--shadow-xs);
}

.banner-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.back-btn {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-color);
  background: var(--bg-surface);
}

.repo-heading-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.repo-title {
  font-size: 18px;
  font-weight: 800;
  margin: 0;
  color: var(--text-main);
  letter-spacing: -0.3px;
}

.owner-name {
  color: var(--text-muted);
  font-weight: 500;
  margin-right: 4px;
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

.branch-tag {
  font-size: 11px;
  color: var(--text-secondary);
  background: var(--bg-subtle);
  padding: 2px 8px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.repo-desc {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 3px;
}

.banner-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.clone-btn {
  border: 1px solid var(--border-color);
  background: var(--bg-surface);
  color: var(--text-main);
}

.ai-toggle-btn {
  background: var(--primary-gradient) !important;
  border: none !important;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.25) !important;
}

/* 主工作区 */
.workspace-layout {
  flex: 1;
  display: flex;
  gap: 14px;
  min-height: 0;
}

/* 文件树面板 */
.file-explorer-pane {
  width: 290px;
  min-width: 290px;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: var(--shadow-xs);
}

.explorer-header {
  height: 48px;
  padding: 0 14px;
  border-bottom: 1px solid var(--border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fafbfc;
}

.explorer-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 700;
  color: var(--text-main);
}

.explorer-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.upload-icon-btn {
  border: 1px solid var(--border-color);
  background: var(--bg-surface);
  color: var(--text-secondary);
  width: 28px;
  height: 28px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.upload-icon-btn:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.tree-search-bar {
  padding: 8px 12px;
  border-bottom: 1px solid var(--border-light);
}

.explorer-tree-box {
  flex: 1;
  overflow-y: auto;
  padding: 6px 0;
}

.tree-empty {
  padding: 40px 20px;
  text-align: center;
  color: var(--text-muted);
  font-size: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

/* 编辑器面板 */
.editor-pane {
  flex: 1;
  min-width: 0;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: var(--shadow-xs);
}

.editor-toolbar {
  height: 48px;
  padding: 0 16px;
  border-bottom: 1px solid var(--border-light);
  background: #fafbfc;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.file-breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-main);
}

.file-path-text {
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
}

.file-size-chip {
  font-size: 11px;
  color: var(--text-muted);
  background: var(--bg-subtle);
  padding: 1px 6px;
  border-radius: 4px;
}

.editor-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.lang-indicator {
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--primary);
  background: var(--primary-light);
  padding: 2px 8px;
  border-radius: var(--radius-full);
}

.editor-btn {
  border: none;
  background: transparent;
  width: 28px;
  height: 28px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.editor-btn:hover {
  background-color: var(--danger-light);
  color: var(--danger);
}

.monaco-wrapper {
  flex: 1;
  min-height: 0;
}

.editor-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  padding: 40px;
}

.empty-file-illu {
  font-size: 48px;
  color: #cbd5e1;
  margin-bottom: 12px;
}

.empty-file-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.empty-file-sub {
  font-size: 13px;
}

/* AI 对话面板侧边抽屉 */
.ai-assistant-pane {
  width: 380px;
  min-width: 380px;
  display: flex;
  flex-direction: column;
}
</style>
