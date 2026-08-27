import request from '../utils/request'

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  email?: string
}

export interface UserInfo {
  id: number
  username: string
  email: string | null
  role: string
  status: string
  createdAt: string
}

export function login(data: LoginRequest) {
  return request.post('/auth/login', data) as Promise<{ token: string; user: UserInfo }>
}

export function register(data: RegisterRequest) {
  return request.post('/auth/register', data) as Promise<UserInfo>
}

export function logout() {
  return request.post('/auth/logout')
}

export function getCurrentUser() {
  return request.get('/auth/me') as Promise<UserInfo>
}
