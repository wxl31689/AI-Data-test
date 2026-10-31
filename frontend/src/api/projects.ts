import { apiClient, type ApiEnvelope } from './client'

export interface Project {
  id: string
  name: string
  description?: string
  status: 'ACTIVE' | 'ARCHIVED'
  createdAt: string
  updatedAt: string
}

export interface CreateProjectPayload {
  name: string
  description?: string
}

export async function listProjects(): Promise<Project[]> {
  const response = await apiClient.get<ApiEnvelope<Project[]>>('/projects')
  return response.data.data
}

export async function createProject(payload: CreateProjectPayload): Promise<Project> {
  const response = await apiClient.post<ApiEnvelope<Project>>('/projects', payload)
  return response.data.data
}

