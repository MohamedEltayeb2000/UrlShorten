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
      createdAt: new Date().toISOString(),
    }))
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(JSON.stringify({ items, page: 0, size: 100, totalItems: 100, totalPages: 1 }), { status: 200 })),
    )
    const t0 = performance.now()
    render(
      <MemoryRouter>
        <ShortenPage />
      </MemoryRouter>,
    )
    await screen.findByRole('table')
    const elapsed = performance.now() - t0
    expect(elapsed).toBeLessThan(budgets.page_render_ms)
  })
})
