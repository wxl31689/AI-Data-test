import axios from 'axios'

export interface ApiEnvelope<T> {
  success: boolean
  code: string
  message: string
  data: T
  traceId: string
}

export const apiClient = axios.create({
  baseURL: '/api/v1',
  timeout: 10_000,
  headers: {
    'Content-Type': 'application/json',
  },
})

