import request from '../utils/request'

export interface CreateRepoRequest {
  name: string
  description?: string
  visibility?: 'PUBLIC' | 'PRIVATE'
}

export interface RepoVO {
  id: number
  name: string
  ownerId: number
  ownerName: string
  description: string | null
  visibility: string
  defaultBranch: string
  starCount: number
  createdAt: string
  updatedAt: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export function createRepo(data: CreateRepoRequest) {
  return request.post('/repos', data) as Promise<RepoVO>
}

export function getRepo(id: number) {
  return request.get(`/repos/${id}`) as Promise<RepoVO>
}

export function listMyRepos(page = 1, size = 10) {
  return request.get('/repos/my', { params: { page, size } }) as Promise<PageResult<RepoVO>>
}

export function listPublicRepos(page = 1, size = 10) {
  return request.get('/repos/public', { params: { page, size } }) as Promise<PageResult<RepoVO>>
}

export function deleteRepo(id: number) {
  return request.delete(`/repos/${id}`)
}
