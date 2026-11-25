import { http, int } from './http'
import type { Page } from './links'

export interface ClickView {
  code: string
  occurredAt: string
  referrer?: string
  userAgent?: string
}

export interface Summary {
  totalLinks: number
  totalClicks: number
  clicksOverTime: { date: string; clicks: number }[]
  topLinks: { code: string; clicks: number }[]
  recentEvents: Page<ClickView>
}

export const getSummary = (page = 0, size = 5) => http<Summary>(`/api/analytics/summary?page=${int(page)}&size=${int(size)}`)
