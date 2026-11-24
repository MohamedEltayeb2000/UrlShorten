// @req 002-FR-014
import { render, screen } from '@testing-library/react'
import { readFileSync } from 'node:fs'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ShortenPage from '../../src/pages/ShortenPage'

const budgets = JSON.parse(readFileSync('../docs/perf/budgets.json', 'utf8'))

describe('page render budget', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('renders the shorten page with data within the render budget', async () => {
    const items = Array.from({ length: 100 }, (_, i) => ({
      code: `code${i}`.padEnd(7, 'x'),
      shortUrl: `http://localhost:8080/code${i}`,
      originalUrl: `https://example.com/${i}`,
      status: 'ACTIVE',
      clicks: i,
      createdAt: new Date().toISOString(),
    }))
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const body = String(input).includes('/api/analytics')
          ? {
              totalLinks: 100,
              totalClicks: 4950,
              clicksOverTime: [],
              topLinks: [{ code: 'code99x', clicks: 99 }],
              recentEvents: { items: [], page: 0, size: 5, totalItems: 0, totalPages: 0 },
            }
          : { items, page: 0, size: 100, totalItems: 100, totalPages: 1 }
        return new Response(JSON.stringify(body), { status: 200 })
      }),
    )
    // Best of three: a single run can be slowed by a busy machine (the gates run next to Maven); the budget is about
    // the page itself, not about CPU contention.
    const timings: number[] = []
    for (let i = 0; i < 3; i++) {
      const t0 = performance.now()
      const { unmount } = render(
        <MemoryRouter>
          <ShortenPage />
        </MemoryRouter>,
      )
      await screen.findByRole('table')
      timings.push(performance.now() - t0)
      unmount()
    }
    expect(Math.min(...timings), `timings ms: ${timings.map((t) => Math.round(t)).join(', ')}`).toBeLessThan(
      budgets.page_render_ms,
    )
  })
})
