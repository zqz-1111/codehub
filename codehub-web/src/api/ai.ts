import request from '../utils/request'

export interface AiChatRequest {
  question: string
}

export interface AiEvent {
  type: 'status' | 'reference' | 'token' | 'error' | 'done'
  data: string
}

export interface Reference {
  file: string
  type: string
  name: string
  fileId: number
}

/**
 * 流式AI问答（NDJSON）
 * 返回一个AsyncGenerator，逐事件yield
 */
export async function* streamAsk(repoId: number, question: string): AsyncGenerator<AiEvent> {
  const token = localStorage.getItem('token')
  const response = await fetch(`/ai/repos/${repoId}/ask`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ question }),
  })

  if (!response.ok) {
    yield { type: 'error', data: `HTTP ${response.status}` }
    return
  }

  const reader = response.body?.getReader()
  if (!reader) {
    yield { type: 'error', data: '无法读取响应流' }
    return
  }

  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''

    for (const line of lines) {
      const trimmed = line.trim()
      if (!trimmed) continue
      try {
        const event = JSON.parse(trimmed) as AiEvent
        yield event
      } catch {
        // 忽略解析错误
      }
    }
  }

  // 处理buffer中剩余的数据
  if (buffer.trim()) {
    try {
      yield JSON.parse(buffer.trim()) as AiEvent
    } catch {}
  }
}

export function getHistory(repoId: number, page = 1, size = 20) {
  return request.get(`/ai/repos/${repoId}/history`, { params: { page, size } })
}
