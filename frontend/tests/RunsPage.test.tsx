// @req 001-FR-021
// @req 002-FR-016
// @req 002-FR-017
import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import RunsPage from '../src/pages/RunsPage'

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const emptyPage = { items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 }
const pageOf = (items: unknown[]) => ({ ...emptyPage, items, totalItems: items.length })

const node = (id: string, name: string, level: number, status = 'PENDING', extra = {}) => ({
  id,
  name,
  status,
  level,
  dependsOn: [],
  attempts: 0,
  ...extra,
})

const makeRun = (status: string, nodes = [node('a', 'Plan', 0)], id = 7) => ({
  id,
  scenario: 'GREENFIELD',
  requirement: 'Build a thing',
  status,
  createdAt: '2026-01-01T00:00:00Z',
  planVersion: 3,
  nodes,
})

type Handler = (init?: RequestInit) => Response | Promise<Response>

function mockFetch(handlers: Record<string, Handler>) {
  const fn = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${String(input).split('?')[0]}`
    const h = handlers[key]
    if (!h) throw new Error(`unexpected request ${key}`)
    return h(init)
  })
  vi.stubGlobal('fetch', fn)
  return fn
}

const renderPage = () =>
  render(
    <MemoryRouter>
      <RunsPage />
    </MemoryRouter>,
  )

describe('RunsPage', () => {
  beforeEach(() => vi.useRealTimers())
  afterEach(() => vi.unstubAllGlobals())

  it('shows an empty state when there are no runs', async () => {
    mockFetch({ 'GET /api/agent-runs': () => json(emptyPage) })
    renderPage()
    expect(await screen.findByText('No runs yet')).toBeInTheDocument()
  })

  it('shows a loading state before the list arrives', async () => {
    mockFetch({ 'GET /api/agent-runs': () => json(emptyPage) })
    renderPage()
    expect(screen.getByText('Loading…')).toBeInTheDocument()
    await screen.findByText('No runs yet')
  })

  it('launches a run with requirement and scenario and shows it', async () => {
    const user = userEvent.setup()
    const fetchMock = mockFetch({
      'GET /api/agent-runs': () => json(emptyPage),
      'POST /api/agent-runs': () => json(makeRun('CREATED'), 201),
    })
    renderPage()
    await screen.findByText('No runs yet')
    await user.type(screen.getByLabelText('Requirement'), 'Build a thing')
    await user.selectOptions(screen.getByLabelText('Scenario'), 'Greenfield')
    await user.click(screen.getByRole('button', { name: 'Launch run' }))

    expect(await screen.findByRole('heading', { name: 'Run #7' })).toBeInTheDocument()
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')!
    expect(JSON.parse(post[1]!.body as string)).toEqual({ requirement: 'Build a thing', scenario: 'GREENFIELD' })
    expect(screen.getByText(/Plan version:/)).toHaveTextContent('Plan version: 3')
  })

  it('groups stages by level, shows status text and a parallel caption', async () => {
    const nodes = [
      node('a', 'Plan', 0, 'SUCCEEDED'),
      node('b', 'Build API', 1, 'RUNNING', { dependsOn: ['a'], attempts: 1 }),
      node('c', 'Build UI', 1, 'FAILED', { dependsOn: ['a'], attempts: 2, errorSummary: 'compile error' }),
    ]
    mockFetch({
      'GET /api/agent-runs': () => json(pageOf([makeRun('RUNNING', nodes)])),
      'GET /api/agent-runs/7': () => json(makeRun('RUNNING', nodes)),
    })
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Open run 7' }))

    const levels = within(await screen.findByRole('list', { name: 'Stages' })).getAllByRole('heading', { level: 4 })
    expect(levels).toHaveLength(2)
    expect(levels[0]).not.toHaveTextContent('Parallel stages')
    expect(levels[1]).toHaveTextContent('Parallel stages')

    const stage = (name: string) => screen.getByText(name).closest('li')!
    expect(stage('Plan')).toHaveTextContent('SUCCEEDED')
    expect(stage('Build API')).toHaveTextContent('RUNNING')
    expect(stage('Build API')).toHaveTextContent('after: Plan')
    expect(stage('Build API')).toHaveTextContent('Attempts: 1')
    expect(stage('Build UI')).toHaveTextContent('FAILED')
    expect(stage('Build UI')).toHaveTextContent('compile error')
  })

  it.each([
    ['CREATED', true, false],
    ['PAUSED', true, false],
    ['RUNNING', false, true],
    ['SUCCEEDED', false, false],
  ])('enables Start/Pause for %s status', async (status, start, pause) => {
    mockFetch({
      'GET /api/agent-runs': () => json(pageOf([makeRun(status)])),
      'GET /api/agent-runs/7': () => json(makeRun(status)),
    })
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Open run 7' }))
    const startBtn = await screen.findByRole('button', { name: 'Start' })
    expect(startBtn).toHaveProperty('disabled', !start)
    expect(screen.getByRole('button', { name: 'Pause' })).toHaveProperty('disabled', !pause)
  })

  it('calls the start and pause endpoints', async () => {
    const user = userEvent.setup()
    const fetchMock = mockFetch({
      'GET /api/agent-runs': () => json(pageOf([makeRun('CREATED')])),
      'GET /api/agent-runs/7': () => json(makeRun('CREATED')),
      'POST /api/agent-runs/7/start': () => json(makeRun('RUNNING')),
      'POST /api/agent-runs/7/pause': () => json(makeRun('PAUSED')),
    })
    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Open run 7' }))
    await user.click(await screen.findByRole('button', { name: 'Start' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Pause' })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: 'Pause' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Start' })).toBeEnabled())
    const calls = fetchMock.mock.calls.map(([url, init]) => `${init?.method ?? 'GET'} ${url}`)
    expect(calls).toContain('POST /api/agent-runs/7/start')
    expect(calls).toContain('POST /api/agent-runs/7/pause')
  })

  it('shows an error with focus when loading fails', async () => {
    mockFetch({
      'GET /api/agent-runs': () => json({ error: { code: 'BOOM', message: 'Could not load runs' } }, 500),
    })
    renderPage()
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Could not load runs')
    await waitFor(() => expect(alert).toHaveFocus())
  })

  it('has labeled form fields reachable with the keyboard', async () => {
    mockFetch({ 'GET /api/agent-runs': () => json(emptyPage) })
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('No runs yet')
    await user.tab()
    expect(screen.getByLabelText('Requirement')).toHaveFocus()
    await user.tab()
    expect(screen.getByLabelText('Scenario')).toHaveFocus()
    await user.tab()
    expect(screen.getByRole('button', { name: 'Launch run' })).toHaveFocus()
  })

  describe('polling', () => {
    beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }))
    afterEach(() => vi.useRealTimers())

    it('refreshes a RUNNING run every 2s and stops once it succeeds', async () => {
      let status = 'RUNNING'
      const fetchMock = mockFetch({
        'GET /api/agent-runs': () => json(pageOf([makeRun('RUNNING')])),
        'GET /api/agent-runs/7': () => json(makeRun(status, [node('a', 'Plan', 0, status)])),
      })
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
      renderPage()
      await user.click(await screen.findByRole('button', { name: 'Open run 7' }))
      const detail = await screen.findByRole('heading', { name: 'Run #7' })
      const section = detail.closest('section')!
      const gets = () => fetchMock.mock.calls.filter(([u, i]) => String(u) === '/api/agent-runs/7' && !i?.method).length
      const before = gets()

      await act(async () => {
        await vi.advanceTimersByTimeAsync(2100)
      })
      expect(gets()).toBe(before + 1)

      status = 'SUCCEEDED'
      await act(async () => {
        await vi.advanceTimersByTimeAsync(2000)
      })
      await waitFor(() => expect(within(section).getAllByText('SUCCEEDED').length).toBeGreaterThan(0))
      const after = gets()
      await act(async () => {
        await vi.advanceTimersByTimeAsync(6000)
      })
      expect(gets()).toBe(after)
    })
  })
})
