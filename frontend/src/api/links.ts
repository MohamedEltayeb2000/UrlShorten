import { http, seg, int } from './http'

export interface Link {
  code: string
  shortUrl: string
  originalUrl: string
  status: string
  createdAt: string
  expiresAt?: string
  clicks: number
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export const createLink = (url: string, alias?: string, expiresAt?: string) =>
  http<Link>('/api/links', {
    method: 'POST',
    body: JSON.stringify({ url, alias: alias || undefined, expiresAt: expiresAt || undefined }),
  })

export const listLinks = (page = 0, size = 20) => http<Page<Link>>(`/api/links?page=${int(page)}&size=${int(size)}`)

export const deactivateLink = (code: string) => http<void>(`/api/links/${seg(code)}`, { method: 'DELETE' })
