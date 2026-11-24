// @req 001-FR-023
// @req 002-FR-016
// @req 002-FR-017
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ShortenPage from '../src/pages/ShortenPage'

function mockFetch(handlers: Record<string, () => Response | Promise<Response>>) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const key = `${init?.method ?? 'GET'} ${String(input).split('?')[0]}`
      const h = handlers[key]
      if (!h) throw new Error(`unexpected request ${key}`)
      return h()
    }),
  )
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const emptyPage = { items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 }

const renderPage = () =>
  render(
    <MemoryRouter>
      <ShortenPage />
    </MemoryRouter>,
  )

describe('ShortenPage', () => {
  beforeEach(() => vi.useRealTimers())
  afterEach(() => vi.unstubAllGlobals())

  it('shows an empty state when there are no links', async () => {
    mockFetch({ 'GET /api/links': () => json(emptyPage) })
    renderPage()
    expect(screen.getByText('Loading…')).toBeInTheDocument()
    expect(await screen.findByText(/No links yet/)).toBeInTheDocument()
  })

  it('has labeled inputs reachable by keyboard', async () => {
    mockFetch({ 'GET /api/links': () => json(emptyPage) })
    renderPage()
    expect(screen.getByLabelText('Long URL')).toBeInTheDocument()
    expect(screen.getByLabelText(/Custom alias/)).toBeInTheDocument()
    const user = userEvent.setup()
    await user.tab()
    expect(screen.getByLabelText('Long URL')).toHaveFocus()
  })

  it('shows the short link and a copy button after a valid submit', async () => {
    const link = {
      code: 'abc1234',
      shortUrl: 'http://localhost:8080/abc1234',
      originalUrl: 'https://example.com',
      status: 'ACTIVE',
      createdAt: new Date().toISOString(),
    }
    mockFetch({
      'GET /api/links': () => json(emptyPage),
      'POST /api/links': () => json(link, 201),
    })
    renderPage()
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Long URL'), 'https://example.com')
    await user.click(screen.getByRole('button', { name: 'Shorten' }))
    expect(await screen.findByRole('status')).toHaveTextContent('http://localhost:8080/abc1234')
    expect(screen.getByRole('button', { name: 'Copy' })).toBeInTheDocument()
  })

  it('shows a clear error message and moves focus to it for an invalid URL', async () => {
    mockFetch({
      'GET /api/links': () => json(emptyPage),
      'POST /api/links': () =>
        json({ error: { code: 'INVALID_URL', message: 'Only http and https URLs are allowed' } }, 400),
    })
    renderPage()
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Long URL'), 'javascript:alert(1)')
    await user.click(screen.getByRole('button', { name: 'Shorten' }))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Only http and https URLs are allowed')
    await waitFor(() => expect(alert).toHaveFocus())
  })

  it('shows an error state when the list cannot be loaded', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('offline') }))
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent(/Cannot reach the server/)
  })
})
