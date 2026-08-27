<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
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
    // 逐个上传分片
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

    // 合并分片
    await request.post(`/repos/${props.repoId}/files/chunks/merge`, null, {
      params: {
        uploadId,
        fileName: file.name,
        path: file.name,
      },
    })

    ElMessage.success('上传成功')
    emit('uploaded')
  } catch (e: any) {
    ElMessage.error('上传失败: ' + (e.message || '未知错误'))
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
  <el-upload
    :show-file-list="false"
    :http-request="handleUpload"
    :disabled="uploading"
    accept="*"
  >
    <el-button :loading="uploading" :icon="Upload">
      {{ uploading ? `上传中 ${progress}%` : '分片上传' }}
    </el-button>
  </el-upload>
  <el-progress v-if="uploading" :percentage="progress" style="margin-top: 8px" />
</template>
