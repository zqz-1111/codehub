<script setup lang="ts">
import { ref } from 'vue'
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
const CHUNK_SIZE = 1024 * 1024 // 1MB per chunk

async function handleUpload(uploadOption: any) {
  const file = uploadOption.file as File
  if (!file) return

  uploading.value = true
  progress.value = 0

  const uploadId = generateUploadId()
  const totalChunks = Math.ceil(file.size / CHUNK_SIZE)

  try {
    for (let i = 0; i < totalChunks; i++) {
      const start = i * CHUNK_SIZE
      const end = Math.min(start + CHUNK_SIZE, file.size)
      const chunk = file.slice(start, end)

      const formData = new FormData()
      formData.append('file', chunk, file.name)
      formData.append('uploadId', uploadId)
      formData.append('chunkIndex', i.toString())
      formData.append('totalChunks', totalChunks.toString())

      await request.post(`/repos/${props.repoId}/files/chunks`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })

      progress.value = Math.round(((i + 1) / totalChunks) * 100)
    }

    await request.post(`/repos/${props.repoId}/files/chunks/merge`, null, {
      params: {
        uploadId,
        fileName: file.name,
        path: file.name,
      },
    })

    ElMessage.success(`大文件「${file.name}」分片秒传并合并完成`)
    emit('uploaded')
  } catch (e: any) {
    ElMessage.error('分片上传失败: ' + (e.message || '网络异常'))
  } finally {
    uploading.value = false
    progress.value = 0
  }
}

function generateUploadId(): string {
  return Date.now().toString(36) + Math.random().toString(36).substring(2, 8)
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
      <button class="chunk-upload-btn" :disabled="uploading" title="1MB 分片大文件秒传">
        <el-icon><Files /></el-icon>
        <span v-if="!uploading">分片秒传</span>
        <span v-else>{{ progress }}%</span>
      </button>
    </el-upload>
  </div>
</template>

<style scoped>
.chunk-uploader-wrap {
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
