import { http } from './http'

export interface Link {
  code: string
  shortUrl: string
  originalUrl: string
  status: string
  createdAt: string
  expiresAt?: string
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export const createLink = (url: string, alias?: string) =>
  http<Link>('/api/links', {
    method: 'POST',
    body: JSON.stringify({ url, alias: alias || undefined }),
  })

export const listLinks = (page = 0, size = 20) => http<Page<Link>>(`/api/links?page=${page}&size=${size}`)
