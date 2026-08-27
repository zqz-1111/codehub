<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { ChatDotRound, Document, Delete, Promotion, Loading } from '@element-plus/icons-vue'
import { streamAsk } from '../api/ai'
import type { Reference } from '../api/ai'

const props = defineProps<{
  repoId: number
}>()

const emit = defineEmits<{
  openFile: [fileId: number]
  close: []
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

const quickPrompts = [
  '这个项目的整体架构和核心模块是什么？',
  '解释当前仓库的业务数据流与异常处理机制',
  '项目中使用了哪些高可靠性并发与缓存设计？'
]

function useQuickPrompt(promptText: string) {
  question.value = promptText
  handleAsk()
}

function clearChat() {
  messages.value = []
}

async function handleAsk() {
  const q = question.value.trim()
  if (!q || loading.value) return

  // 添加用户消息
  messages.value.push({ role: 'user', content: q })
  question.value = ''

  // 添加AI消息占位
  const aiMsg: Message = { role: 'ai', content: '', references: [], status: '正在分析语义索引...' }
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
    aiMsg.content = `**请求失败**: ${e.message || '网络或接口异常'}`
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

// 优化的Markdown渲染
function renderMarkdown(text: string): string {
  if (!text) return ''
  return text
    .replace(/```(\w*)\n([\s\S]*?)```/g, (_match, lang, code) => {
      return `<div class="code-block-wrapper"><div class="code-block-header"><span>${lang || 'CODE'}</span></div><pre><code class="lang-${lang}">${code.replace(/</g, '&lt;').replace(/>/g, '&gt;')}</code></pre></div>`
    })
    .replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br>')
}
</script>

<template>
  <div class="ai-panel-box animate-fade-in">
    <!-- 头部 -->
    <div class="ai-header">
      <div class="header-left">
        <div class="ai-avatar">
          <el-icon><ChatDotRound /></el-icon>
        </div>
        <div>
          <div class="ai-title">CodeHub AI 智能助手</div>
          <div class="ai-sub">Qwen 3.7 Plus RAG 流式理解引擎</div>
        </div>
      </div>

      <div class="header-actions">
        <button v-if="messages.length > 0" class="icon-btn" title="清空对话" @click="clearChat">
          <el-icon><Delete /></el-icon>
        </button>
        <button class="icon-btn" title="关闭面板" @click="emit('close')">
          <el-icon><Close /></el-icon>
        </button>
      </div>
    </div>

    <!-- 消息对话区域 -->
    <div ref="chatContainer" class="ai-messages-area">
      <!-- 初始欢迎空状态 -->
      <div v-if="messages.length === 0" class="welcome-box">
        <div class="welcome-icon">
          <el-icon><ChatDotRound /></el-icon>
        </div>
        <h3 class="welcome-title">我可以帮您解析仓库代码</h3>
        <p class="welcome-desc">基于代码语义切分与向量检索，实时解答逻辑疑问、类依赖与实现原理。</p>

        <div class="prompt-chips">
          <div class="chip-label">快捷提问：</div>
          <button
            v-for="(p, i) in quickPrompts"
            :key="i"
            class="prompt-chip"
            @click="useQuickPrompt(p)"
          >
            {{ p }}
          </button>
        </div>
      </div>

      <!-- 消息列表 -->
      <div v-for="(msg, idx) in messages" :key="idx" :class="['message-row', msg.role]">
        <div class="avatar-col">
          <div v-if="msg.role === 'user'" class="user-avatar">我</div>
          <div v-else class="ai-avatar-msg">
            <el-icon><ChatDotRound /></el-icon>
          </div>
        </div>

        <div class="content-col">
          <div class="bubble-container">
            <!-- 引用文件卡片 -->
            <div v-if="msg.references && msg.references.length > 0" class="reference-box">
              <span class="ref-title">代码上下文引用 ({{ msg.references.length }})：</span>
              <div class="ref-chips">
                <span
                  v-for="ref in msg.references"
                  :key="ref.fileId"
                  class="ref-chip"
                  @click="emit('openFile', ref.fileId)"
                >
                  <el-icon><Document /></el-icon>
                  {{ ref.file }}
                </span>
              </div>
            </div>

            <!-- 思考状态 -->
            <div v-if="msg.status" class="thinking-state">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>{{ msg.status }}</span>
            </div>

            <!-- 正文 Markdown -->
            <div v-else class="markdown-body" v-html="renderMarkdown(msg.content)"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部输入框 -->
    <div class="ai-footer">
      <el-input
        v-model="question"
        type="textarea"
        :rows="2"
        placeholder="向 AI 提问代码库架构或功能实现... (Enter 发送, Shift+Enter 换行)"
        :disabled="loading"
        class="chat-input"
        @keydown="handleKeydown"
      />
      <el-button
        type="primary"
        class="send-btn"
        :loading="loading"
        :disabled="!question.trim()"
        @click="handleAsk"
      >
        <el-icon><Promotion /></el-icon>
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.ai-panel-box {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  overflow: hidden;
}

/* 头部 */
.ai-header {
  height: 56px;
  padding: 0 16px;
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ai-avatar {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-md);
  background: var(--primary-gradient);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
}

.ai-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-main);
}

.ai-sub {
  font-size: 11px;
  color: var(--text-muted);
}

.header-actions {
  display: flex;
  gap: 6px;
}

.icon-btn {
  border: none;
  background: transparent;
  width: 30px;
  height: 30px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.icon-btn:hover {
  background-color: var(--bg-subtle);
  color: var(--text-main);
}

/* 消息区域 */
.ai-messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: #fcfdfe;
}

/* 欢迎状态 */
.welcome-box {
  margin: auto;
  text-align: center;
  max-width: 480px;
  padding: 20px;
}

.welcome-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--primary-light);
  color: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  margin: 0 auto 12px;
}

.welcome-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
  margin-bottom: 6px;
}

.welcome-desc {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
  margin-bottom: 20px;
}

.prompt-chips {
  display: flex;
  flex-direction: column;
  gap: 8px;
  text-align: left;
}

.chip-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
}

.prompt-chip {
  padding: 8px 12px;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  font-size: 12px;
  color: var(--text-secondary);
  text-align: left;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.prompt-chip:hover {
  border-color: var(--primary);
  color: var(--primary);
  background: var(--primary-light);
}

/* 消息行 */
.message-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.message-row.user {
  flex-direction: row-reverse;
}

.avatar-col {
  flex-shrink: 0;
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: #3b82f6;
  color: white;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ai-avatar-msg {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--primary-gradient);
  color: white;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.content-col {
  max-width: 85%;
}

.message-row.user .bubble-container {
  background: var(--primary-gradient);
  color: white;
  padding: 10px 14px;
  border-radius: 14px 14px 2px 14px;
  font-size: 13px;
  line-height: 1.5;
  box-shadow: 0 2px 8px rgba(79, 70, 229, 0.2);
}

.message-row.ai .bubble-container {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  padding: 14px 16px;
  border-radius: 14px 14px 14px 2px;
  font-size: 13px;
  line-height: 1.6;
  box-shadow: var(--shadow-xs);
  color: var(--text-main);
}

/* 引用文件卡片 */
.reference-box {
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--border-color);
}

.ref-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  display: block;
  margin-bottom: 6px;
}

.ref-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.ref-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  background: #f1f5f9;
  border-radius: 4px;
  font-size: 11px;
  color: #0284c7;
  cursor: pointer;
  font-family: 'JetBrains Mono', monospace;
  transition: all var(--transition-fast);
}

.ref-chip:hover {
  background: #e0f2fe;
}

/* 思考中状态 */
.thinking-state {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
  font-size: 12px;
  padding: 4px 0;
}

/* Markdown渲染美化 */
.markdown-body :deep(strong) {
  color: var(--text-main);
  font-weight: 700;
}

.markdown-body :deep(.inline-code) {
  background: #f1f5f9;
  color: #d97706;
  padding: 2px 5px;
  border-radius: 4px;
  font-size: 12px;
  font-family: 'JetBrains Mono', monospace;
}

.markdown-body :deep(.code-block-wrapper) {
  margin: 10px 0;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-color);
  background: #f8fafc;
  overflow: hidden;
}

.markdown-body :deep(.code-block-header) {
  padding: 6px 12px;
  background: #edf2f7;
  font-size: 10px;
  font-weight: 700;
  color: var(--text-muted);
  border-bottom: 1px solid var(--border-light);
}

.markdown-body :deep(pre) {
  padding: 10px 12px;
  margin: 0;
  overflow-x: auto;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  line-height: 1.5;
  color: #1e293b;
}

/* 底部输入框 */
.ai-footer {
  padding: 12px 16px;
  background: var(--bg-surface);
  border-top: 1px solid var(--border-light);
  display: flex;
  gap: 10px;
  align-items: flex-end;
}

.chat-input {
  flex: 1;
}

.send-btn {
  height: 52px;
  width: 52px;
  border-radius: var(--radius-md);
  background: var(--primary-gradient) !important;
  border: none !important;
  font-size: 18px;
  box-shadow: 0 2px 8px rgba(79, 70, 229, 0.25) !important;
}
</style>
