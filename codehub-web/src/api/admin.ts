import request from '../utils/request'

// ========== 用户管理 ==========
export interface AdminUser {
  id: number
  username: string
  email: string
  role: string
  status: string
  createdAt: string
}

export function listUsers(page = 1, size = 10, keyword?: string) {
  return request.get('/admin/users', { params: { page, size, keyword } }) as Promise<any>
}

export function updateUserRole(userId: number, role: string) {
  return request.put(`/admin/users/${userId}/role`, null, { params: { role } })
}

export function updateUserStatus(userId: number, status: string) {
  return request.put(`/admin/users/${userId}/status`, null, { params: { status } })
}

export function resetPassword(userId: number, newPassword: string) {
  return request.put(`/admin/users/${userId}/password`, null, { params: { newPassword } })
}

// ========== 仓库管理 ==========
export function listAllRepos(page = 1, size = 10) {
  return request.get('/admin/repos', { params: { page, size } }) as Promise<any>
}

export function forceDeleteRepo(repoId: number) {
  return request.delete(`/admin/repos/${repoId}`)
}

export function updateRepoVisibility(repoId: number, visibility: string) {
  return request.put(`/admin/repos/${repoId}/visibility`, null, { params: { visibility } })
}

// ========== 模型配置 ==========
export interface ModelConfig {
  id: number
  provider: string
  modelName: string
  baseUrl: string
  apiKey: string
  enabled: boolean
  createdAt: string
}

export interface ModelConfigDTO {
  provider: string
  modelName: string
  baseUrl: string
  apiKey: string
  enabled: boolean
}

export function listModelConfigs(page = 1, size = 10) {
  return request.get('/admin/models', { params: { page, size } }) as Promise<any>
}

export function createModelConfig(data: ModelConfigDTO) {
  return request.post('/admin/models', data) as Promise<ModelConfig>
}

export function updateModelConfig(id: number, data: ModelConfigDTO) {
  return request.put(`/admin/models/${id}`, data) as Promise<ModelConfig>
}

export function deleteModelConfig(id: number) {
  return request.delete(`/admin/models/${id}`)
}

export function toggleModelEnabled(id: number, enabled: boolean) {
  return request.put(`/admin/models/${id}/toggle`, null, { params: { enabled } })
}

// ========== 审计日志 ==========
export interface AuditLog {
  id: number
  userId: number
  action: string
  targetType: string
  targetId: number
  detail: string
  ip: string
  createdAt: string
}

export function listAuditLogs(page = 1, size = 20, action?: string, userId?: number) {
  return request.get('/admin/audit-logs', { params: { page, size, action, userId } }) as Promise<any>
}

// ========== 统计数据 ==========
export function getDashboardStats() {
  return request.get('/admin/stats') as Promise<Record<string, number>>
}

export function getDailyStats(days = 7) {
  return request.get('/admin/stats/daily', { params: { days } }) as Promise<any[]>
}
