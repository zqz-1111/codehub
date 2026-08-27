import request from '../utils/request'

export interface FileVO {
  id: number
  path: string
  mimeType: string | null
  sizeBytes: number | null
  commitId: number | null
  createdAt: string
}

export function uploadFile(repoId: number, file: File, path: string) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('path', path)
  return request.post(`/repos/${repoId}/files`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }) as Promise<FileVO>
}

export function listFiles(repoId: number) {
  return request.get(`/repos/${repoId}/files`) as Promise<FileVO[]>
}

export function getFileContent(repoId: number, fileId: number) {
  return request.get(`/repos/${repoId}/files/${fileId}/content`) as Promise<string>
}

export function getDownloadUrl(repoId: number, fileId: number) {
  return request.get(`/repos/${repoId}/files/${fileId}/download`) as Promise<string>
}

export function deleteFile(repoId: number, fileId: number) {
  return request.delete(`/repos/${repoId}/files/${fileId}`)
}
