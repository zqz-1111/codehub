<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Upload } from '@element-plus/icons-vue'
import { getRepo } from '../api/repo'
import { listFiles, getFileContent, uploadFile, deleteFile } from '../api/file'
import type { RepoVO } from '../api/repo'
import type { FileVO } from '../api/file'
import MonacoEditor from '../components/MonacoEditor.vue'
import ChunkUploader from '../components/ChunkUploader.vue'
import FileTreeNode from '../components/FileTreeNode.vue'
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

// AI面板
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

// 构建文件树结构
interface TreeNode {
  name: string
  path: string
  type: 'file' | 'dir'
  children?: TreeNode[]
  file?: FileVO
}

const expandedDirs = ref<Set<string>>(new Set())

const fileTree = computed<TreeNode[]>(() => {
  const root: TreeNode[] = []
  const dirMap = new Map<string, TreeNode>()

  // 排序：目录在前，文件在后；同类型按名称排序
  const sorted = [...files.value].sort((a, b) => {
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
        // 文件节点
        current.push({ name: part, path: f.path, type: 'file', file: f })
      } else {
        // 目录节点
        let dir = dirMap.get(currentPath)
        if (!dir) {
          dir = { name: part, path: currentPath, type: 'dir', children: [] }
          dirMap.set(currentPath, dir)
          current.push(dir)
          // 默认展开第一级目录
          if (i === 0) expandedDirs.value.add(currentPath)
        }
        current = dir.children!
      }
    }
  }
  return root
})

// 根据文件扩展名推断语言
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
  } catch {
    // 接口可能未实现，忽略
  }
}

// 点击文件/目录
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
  currentFile.value = node.file
  fileLoading.value = true
  try {
    fileContent.value = await getFileContent(repoId, node.file.id)
  } catch {
    fileContent.value = '// 文件内容加载失败'
  } finally {
    fileLoading.value = false
  }
}

// 上传文件
async function handleUpload(uploadFile_: any) {
  const file = uploadFile_.file as File
  const path = file.name
  try {
    await uploadFile(repoId, file, path)
    ElMessage.success('上传成功')
    loadFiles()
  } catch {
    ElMessage.error('上传失败')
  }
}

// 删除文件
async function handleDeleteFile(file: { id: number }) {
  try {
    await deleteFile(repoId, file.id)
    ElMessage.success('已删除')
    if (currentFile.value?.id === file.id) {
      currentFile.value = null
      fileContent.value = ''
    }
    loadFiles()
  } catch {
    ElMessage.error('删除失败')
  }
}

// 格式化文件大小
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
  <div v-loading="loading">
    <!-- 顶部仓库信息栏 -->
    <div class="repo-header">
      <div style="display: flex; align-items: center; gap: 12px">
        <el-button :icon="ArrowLeft" link @click="router.push('/repositories')" />
        <h2 style="margin: 0">
          <span style="color: #606266">{{ repo?.ownerName }} /</span>
          {{ repo?.name }}
        </h2>
        <el-tag :type="repo?.visibility === 'PUBLIC' ? 'success' : 'info'" size="small">
          {{ repo?.visibility }}
        </el-tag>
      </div>
      <p v-if="repo?.description" style="color: #909399; margin: 8px 0 0 36px">
        {{ repo.description }}
      </p>
    </div>

    <!-- 主体区域 -->
    <div class="repo-body">
      <!-- 左侧文件树 -->
      <div class="file-tree">
        <div class="tree-header">
          <span style="font-weight: 600">文件</span>
          <div style="display: flex; gap: 4px; align-items: center">
            <el-tag size="small" type="info">{{ repo?.defaultBranch }}</el-tag>
            <el-upload
              :show-file-list="false"
              :http-request="handleUpload"
              accept="*"
            >
              <el-button size="small" :icon="Upload" circle />
            </el-upload>
            <ChunkUploader :repo-id="repoId" @uploaded="loadFiles" />
          </div>
        </div>

        <div class="tree-content">
          <template v-if="fileTree.length === 0">
            <div style="padding: 20px; text-align: center; color: #909399; font-size: 13px">
              暂无文件，点击上方上传
            </div>
          </template>
          <FileTreeNode
            :nodes="fileTree"
            :expanded-dirs="expandedDirs"
            :current-path="currentFile?.path || null"
            @click="clickNode"
            @delete="handleDeleteFile"
          />
        </div>
      </div>

      <!-- 右侧代码展示区 -->
      <div class="code-panel" v-loading="fileLoading">
        <template v-if="currentFile">
          <div class="code-header">
            <span>{{ currentFile.path }}</span>
            <span style="color: #909399; font-size: 12px">
              {{ formatSize(currentFile.sizeBytes) }}
            </span>
          </div>
          <MonacoEditor
            :code="fileContent"
            :language="getLanguage(currentFile.path)"
            :read-only="true"
          />
        </template>
        <template v-else>
          <div class="code-empty">
            <el-icon :size="48" color="#c0c4cc"><Document /></el-icon>
            <p>点击左侧文件查看代码</p>
          </div>
        </template>
      </div>
    </div>

    <!-- AI助手面板 -->
    <div v-if="showAiPanel" class="ai-panel-container">
      <AiPanel :repo-id="repoId" @open-file="openFileFromRef" />
    </div>

    <!-- 底部 Ask AI 按钮 -->
    <div class="ask-ai-bar">
      <el-button type="primary" size="large" @click="showAiPanel = !showAiPanel">
        {{ showAiPanel ? '关闭 AI 助手' : 'Ask AI' }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.repo-header {
  padding: 16px 0;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 16px;
}

.repo-body {
  display: flex;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  min-height: 500px;
}

.file-tree {
  width: 280px;
  border-right: 1px solid #ebeef5;
  flex-shrink: 0;
}

.tree-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #ebeef5;
  background: #fafafa;
}

.tree-content {
  max-height: 600px;
  overflow-y: auto;
  overflow-x: hidden;
}

.code-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.code-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  background: #fafafa;
  border-bottom: 1px solid #ebeef5;
  font-size: 13px;
  color: #606266;
}

.code-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
}

.ask-ai-bar {
  margin-top: 16px;
  padding: 16px;
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  text-align: center;
}

.ai-panel-container {
  height: 500px;
  margin-top: 16px;
}
</style>
