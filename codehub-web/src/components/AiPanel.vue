<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Document } from '@element-plus/icons-vue'
import { streamAsk } from '../api/ai'
import type { Reference } from '../api/ai'

const props = defineProps<{
  repoId: number
}>()

const emit = defineEmits<{
  openFile: [fileId: number]
}>()

interface Message {
  role: 'user' | 'ai'
  content: string
  references?: Reference[]
  status?: string
}

const messages = ref<Message[]>([])
const question = ref('')
const loading = ref(false)
const chatContainer = ref<HTMLElement | null>(null)

async function handleAsk() {
  const q = question.value.trim()
  if (!q || loading.value) return

  // 添加用户消息
  messages.value.push({ role: 'user', content: q })
  question.value = ''

  // 添加AI消息占位
  const aiMsg: Message = { role: 'ai', content: '', references: [], status: '思考中...' }
  messages.value.push(aiMsg)
  loading.value = true

  await nextTick()
  scrollToBottom()

  try {
    for await (const event of streamAsk(props.repoId, q)) {
      switch (event.type) {
        case 'status':
          aiMsg.status = event.data
          break
        case 'reference':
          try {
            aiMsg.references = JSON.parse(event.data)
          } catch {}
          break
        case 'token':
          aiMsg.content += event.data
          aiMsg.status = undefined
          await nextTick()
          scrollToBottom()
          break
        case 'error':
          aiMsg.content = `**错误**: ${event.data}`
          aiMsg.status = undefined
          break
        case 'done':
          aiMsg.status = undefined
          break
      }
    }
  } catch (e: any) {
    aiMsg.content = `**请求失败**: ${e.message}`
  } finally {
    loading.value = false
    aiMsg.status = undefined
  }
}

function scrollToBottom() {
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleAsk()
  }
}

// 简单的Markdown渲染（处理代码块和粗体）
function renderMarkdown(text: string): string {
  return text
    .replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code class="lang-$1">$2</code></pre>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br>')
}
</script>

<template>
  <div class="ai-panel">
    <!-- 标题 -->
    <div class="ai-header">
      <el-icon><ChatDotRound /></el-icon>
      <span>AI 代码助手</span>
    </div>

    <!-- 对话区域 -->
    <div ref="chatContainer" class="ai-messages">
      <div v-if="messages.length === 0" class="ai-empty">
        <p>基于仓库代码回答问题</p>
        <p style="font-size: 12px; color: #909399">
          例如：这个项目的架构是什么？UserService 怎么用？
        </p>
      </div>

      <div v-for="(msg, i) in messages" :key="i" :class="['ai-msg', msg.role]">
        <!-- 用户消息 -->
        <div v-if="msg.role === 'user'" class="msg-content user-content">
          {{ msg.content }}
        </div>

        <!-- AI消息 -->
        <div v-else class="msg-content ai-content">
          <!-- 引用文件 -->
          <div v-if="msg.references && msg.references.length > 0" class="references">
            <span class="ref-label">引用文件：</span>
            <el-button
              v-for="ref in msg.references"
              :key="ref.fileId"
              link
              size="small"
              type="primary"
              @click="emit('openFile', ref.fileId)"
            >
              <el-icon><Document /></el-icon>
              {{ ref.file }}
            </el-button>
          </div>
          <!-- 状态提示 -->
          <div v-if="msg.status" class="ai-status">{{ msg.status }}</div>
          <!-- 内容 -->
          <div v-else v-html="renderMarkdown(msg.content)"></div>
        </div>
      </div>
    </div>

    <!-- 输入框 -->
    <div class="ai-input">
      <el-input
        v-model="question"
        type="textarea"
        :rows="2"
        placeholder="输入问题，按 Enter 发送..."
        :disabled="loading"
        @keydown="handleKeydown"
      />
      <el-button
        type="primary"
        :loading="loading"
        :disabled="!question.trim()"
        @click="handleAsk"
      >
        发送
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.ai-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fff;
}

.ai-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid #ebeef5;
  background: #fafafa;
  font-weight: 600;
}

.ai-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.ai-empty {
  text-align: center;
  color: #c0c4cc;
  padding: 40px 0;
}

.ai-msg {
  margin-bottom: 16px;
}

.msg-content {
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.6;
  max-width: 85%;
}

.user-content {
  background: #409eff;
  color: #fff;
  margin-left: auto;
  border-bottom-right-radius: 2px;
}

.ai-content {
  background: #f5f7fa;
  color: #303133;
  border-bottom-left-radius: 2px;
}

.ai-content :deep(pre) {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
  font-size: 13px;
  margin: 8px 0;
}

.ai-content :deep(code) {
  background: #e8e8e8;
  padding: 2px 4px;
  border-radius: 3px;
  font-size: 13px;
}

.ai-content :deep(pre code) {
  background: none;
  padding: 0;
}

.ai-status {
  color: #909399;
  font-size: 13px;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.references {
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.ref-label {
  font-size: 12px;
  color: #909399;
}

.ai-input {
  display: flex;
  gap: 8px;
  padding: 12px;
  border-top: 1px solid #ebeef5;
  align-items: flex-end;
}

.ai-input .el-input {
  flex: 1;
}
</style>
