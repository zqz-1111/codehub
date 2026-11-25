<script setup lang="ts">
import { ref, shallowRef } from 'vue'
import { ElMessage } from 'element-plus'
import { Files } from '@element-plus/icons-vue'
import request from '../utils/request'

const props = defineProps<{
  repoId: number
}>()

const emit = defineEmits<{
  uploaded: []
}>()

const uploading = ref(false)
const progress = ref(0)
const CHUNK_SIZE = 1024 * 1024 // 每片 1 MiB

interface UploadSession {
  file: File
  repoId: number
  uploadId: string
  totalChunks: number
  nextChunk: number
}

const failedSession = shallowRef<UploadSession | null>(null)

async function handleUpload({ file }: { file: File }) {
  if (uploading.value) return
  if (file.size === 0) {
    ElMessage.warning('请选择非空文件')
    return
  }

  return uploadFile({
    file,
    repoId: props.repoId,
    uploadId: generateUploadId(),
    totalChunks: Math.ceil(file.size / CHUNK_SIZE),
    nextChunk: 0,
  })
}

function retryUpload() {
  const session = failedSession.value
  if (session) return uploadFile(session)
}

async function uploadFile(session: UploadSession) {
  if (uploading.value) return
  const { file, repoId, uploadId, totalChunks } = session
  failedSession.value = null
  uploading.value = true
  progress.value = Math.floor((session.nextChunk / totalChunks) * 100)
  const chunksUrl = `/repos/${repoId}/files/chunks`

  try {
    while (session.nextChunk < totalChunks) {
      const chunkIndex = session.nextChunk
      const start = chunkIndex * CHUNK_SIZE
      const end = Math.min(start + CHUNK_SIZE, file.size)
      const chunk = file.slice(start, end)

      const formData = new FormData()
      formData.append('file', chunk, file.name)
      formData.append('uploadId', uploadId)
      formData.append('chunkIndex', chunkIndex.toString())
      formData.append('totalChunks', totalChunks.toString())

      await request.post(chunksUrl, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })

      session.nextChunk += 1
      progress.value = Math.floor((session.nextChunk / totalChunks) * 100)
    }

    await request.post(`${chunksUrl}/merge`, null, {
      params: { uploadId, fileName: file.name, path: file.name },
      timeout: 120000,
    })

    ElMessage.success(`大文件「${file.name}」分片上传并合并完成`)
    emit('uploaded')
  } catch (error) {
    failedSession.value = session
    const stage = session.nextChunk < totalChunks ? `第 ${session.nextChunk + 1} 片上传` : '分片合并'
    const detail = error instanceof Error && error.message ? error.message : '网络异常'
    ElMessage.error(`${stage}失败：${detail}`)
  } finally {
    uploading.value = false
    progress.value = 0
  }
}


function generateUploadId(): string {
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), byte => byte.toString(16).padStart(2, '0')).join('')
}
</script>

<template>
  <div class="chunk-uploader-wrap">
    <el-upload
      :show-file-list="false"
      :http-request="handleUpload"
      :disabled="uploading"
      accept="*"
    >
      <button type="button" class="chunk-upload-btn" :disabled="uploading" title="1MiB 分片上传">
        <el-icon><Files /></el-icon>
        <span v-if="!uploading">分片上传</span>
        <span v-else-if="progress === 100">正在合并</span>
        <span v-else>{{ progress }}%</span>
      </button>
    </el-upload>
    <button
      v-if="failedSession"
      type="button"
      class="chunk-upload-btn"
      :title="failedSession.file.name"
      @click="retryUpload"
    >
      {{ failedSession.nextChunk < failedSession.totalChunks
        ? `从第 ${failedSession.nextChunk + 1} 片继续`
        : '重试合并' }}
    </button>
  </div>
</template>

<style scoped>
.chunk-uploader-wrap {
  gap: 4px;
  display: inline-flex;
  align-items: center;
}

.chunk-upload-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 8px;
  border-radius: 6px;
  border: 1px solid var(--border-color);
  background: var(--bg-surface);
  color: var(--primary);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.chunk-upload-btn:hover:not(:disabled) {
  border-color: var(--primary);
  background-color: var(--primary-light);
}

.chunk-upload-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
