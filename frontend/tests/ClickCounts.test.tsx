// @req 001-FR-027
// @req 001-FR-026
// @req 002-FR-017
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ShortenPage from '../src/pages/ShortenPage'

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const link = (code: string, clicks: number, status = 'ACTIVE') => ({
  code,
  shortUrl: `http://localhost:8080/${code}`,
  originalUrl: `https://example.com/${code}`,
  status,
  createdAt: new Date().toISOString(),
  clicks,
})

const summary = {
  totalLinks: 2,
  totalClicks: 7,
  clicksOverTime: [{ date: '2026-11-24', clicks: 7 }],
  topLinks: [{ code: 'alpha01', clicks: 5 }],
  recentEvents: {
    items: [{ code: 'alpha01', occurredAt: '2026-11-24T12:00:00Z', referrer: 'https://ref.example' }],
    page: 0,
    size: 5,
    totalItems: 1,
    totalPages: 1,
  },
}

afterEach(() => vi.unstubAllGlobals())

function setup(calls: string[] = []) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const key = `${init?.method ?? 'GET'} ${String(input).split('?')[0]}`
      calls.push(key)
      if (key === 'GET /api/links')
        return json({ items: [link('alpha01', 5), link('beta002', 2)], page: 0, size: 20, totalItems: 2, totalPages: 1 })
      if (key === 'GET /api/analytics/summary') return json(summary)
      if (key === 'DELETE /api/links/alpha01') return new Response(null, { status: 204 })
      throw new Error(`unexpected ${key}`)
    }),
  )
  render(
    <MemoryRouter>
      <ShortenPage />
    </MemoryRouter>,
  )
}

describe('click counts and analytics panel', () => {
  it('shows a clicks column with each link count', async () => {
    setup()
    const table = await screen.findByRole('table')
    expect(within(table).getByRole('columnheader', { name: 'Clicks' })).toBeInTheDocument()
    const row = within(table).getByRole('row', { name: /alpha01/ })
    expect(within(row).getByText('5')).toBeInTheDocument()
  })

  it('shows analytics totals, top links and recent clicks', async () => {
    setup()
    expect(await screen.findByText(/total clicks across/)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Analytics' })).toBeInTheDocument()
    expect(screen.getByText('alpha01: 5 clicks')).toBeInTheDocument()
    expect(screen.getByText(/from https:\/\/ref\.example/)).toBeInTheDocument()
  })

  it('has labeled, keyboard-operable deactivate buttons with accessible names', async () => {
    const calls: string[] = []
    setup(calls)
    await screen.findByRole('table')
    const btn = screen.getByRole('button', { name: 'Deactivate alpha01' })
    btn.focus()
    expect(btn).toHaveFocus()
    await userEvent.setup().keyboard('{Enter}')
    expect(calls).toContain('DELETE /api/links/alpha01')
  })

  it('offers an optional, labeled expiry field', async () => {
    setup()
    expect(await screen.findByLabelText('Expires at (optional)')).toBeInTheDocument()
  })

  it('shows a clear message when analytics cannot be loaded without hiding the link list', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) =>
        String(input).includes('/api/analytics')
          ? json({ error: { code: 'RATE_LIMITED', message: 'Too many requests' } }, 429)
          : json({ items: [link('alpha01', 1)], page: 0, size: 20, totalItems: 1, totalPages: 1 }),
      ),
    )
    render(
      <MemoryRouter>
        <ShortenPage />
      </MemoryRouter>,
    )
    expect(await screen.findByText(/Analytics unavailable: Too many requests/)).toBeInTheDocument()
    expect(await screen.findByRole('table')).toBeInTheDocument()
  })
})
