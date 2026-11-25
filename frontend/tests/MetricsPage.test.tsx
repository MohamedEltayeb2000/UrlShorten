// @req 001-FR-018
// @req 001-FR-020
// @req 001-FR-021
// @req 001-FR-022
// @req 002-FR-017
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import MetricsPage from '../src/pages/MetricsPage'

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const overall = {
  runsTotal: 6, runsSucceeded: 5, runsFailed: 1, runsInProgress: 0, successRate: 0.8333,
  retries: 2, rollbacks: 1, fallbacks: 3, failedStages: 1, blockedStages: 0,
  avgEndToEndMs: 1400, p95StageLatencyMs: 250, mttrMs: null,
}
const scenarios = [
  { id: 'GREENFIELD', name: 'Greenfield', description: 'Brand new service', requirement: 'Build a URL shortener' },
  { id: 'BROWNFIELD', name: 'Brownfield', description: 'Extend existing code', requirement: 'Add click counts' },
  { id: 'AMBIGUOUS', name: 'Ambiguous', description: 'Vague ask', requirement: 'Make it better' },
]
const runs = { items: [{ id: 7, requirement: 'Build it', status: 'SUCCEEDED', createdAt: 'x' }], page: 0, size: 20, totalItems: 1, totalPages: 1 }
const runMetrics = {
  runId: 7, status: 'SUCCEEDED', endToEndMs: 65000, stagesTotal: 3, stagesSucceeded: 3,
  retries: 1, rollbacks: 0, fallbacks: 2, failedStages: 0, blockedStages: 0, waitingStages: 0, mttrMs: 500,
  approvals: { approved: 2, rejected: 1, pending: 0 },
  stageLatencies: [
    { nodeId: 'plan', name: 'Planning', attempts: 1, durationMs: 800 },
    { nodeId: 'build', name: 'Building', attempts: 2, durationMs: 2500 },
  ],
}

afterEach(() => vi.unstubAllGlobals())

type Routes_ = Record<string, () => Response>
function setup(routes: Routes_ = {}, url = '/metrics') {
  const table: Routes_ = {
    '/api/metrics': () => json(overall),
    '/api/scenarios': () => json(scenarios),
    '/api/agent-runs?page=0&size=20': () => json(runs),
    '/api/agent-runs/7/metrics': () => json(runMetrics),
    '/api/agent-runs/7/summary': () => new Response('# Summary\nAll good', { headers: { 'Content-Type': 'text/markdown' } }),
    ...routes,
  }
  const fn = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    if (init?.method === 'POST') return json({ id: 9, requirement: 'Build a URL shortener', status: 'CREATED' }, 201)
    const h = table[String(input)]
    return h ? h() : json({ error: { code: 'NOT_FOUND', message: 'nope' } }, 404)
  })
  vi.stubGlobal('fetch', fn)
  render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path="/metrics" element={<MetricsPage />} />
        <Route path="/runs" element={<h2>Runs destination</h2>} />
      </Routes>
    </MemoryRouter>,
  )
  return fn
}

const tile = (name: string) => screen.findByRole('group', { name })

describe('MetricsPage', () => {
  it('shows overall tiles with formatted values and n/a for null', async () => {
    setup()
    expect(within(await tile('Success rate')).getByText('83%')).toBeInTheDocument()
    expect(within(await tile('Average end-to-end')).getByText('1.4 s')).toBeInTheDocument()
    expect(within(await tile('p95 stage latency')).getByText('250 ms')).toBeInTheDocument()
    expect(within(await tile('MTTR')).getByText('n/a')).toBeInTheDocument()
    expect(within(await tile('Rollbacks')).getByText('1')).toBeInTheDocument()
  })

  it('shows a loading state before data arrives', () => {
    setup({ '/api/metrics': () => json(overall) })
    expect(screen.getByText('Loading metrics…')).toBeInTheDocument()
    expect(screen.getByText('Loading scenarios…')).toBeInTheDocument()
  })

  it('shows empty states', async () => {
    setup({
      '/api/metrics': () => json({ ...overall, runsTotal: 0 }),
      '/api/scenarios': () => json([]),
      '/api/agent-runs?page=0&size=20': () => json({ ...runs, items: [] }),
    })
    expect(await screen.findByText(/no metrics to show/)).toBeInTheDocument()
    expect(await screen.findByText('No scenarios available')).toBeInTheDocument()
    expect(await screen.findByText('No runs yet')).toBeInTheDocument()
  })

  it('shows an error alert and moves focus to it', async () => {
    setup({ '/api/metrics': () => json({ error: { code: 'X', message: 'Metrics unavailable' } }, 500) })
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Metrics unavailable')
    await waitFor(() => expect(alert).toHaveFocus())
  })

  it('renders scenario cards and launches with only the scenario, then navigates to /runs', async () => {
    const user = userEvent.setup()
    const fn = setup()
    const card = within(await screen.findByRole('article', { name: 'Greenfield' }))
    expect(card.getByText('Brand new service')).toBeInTheDocument()
    expect(card.getByText('Build a URL shortener')).toBeInTheDocument()
    expect(screen.getAllByRole('article')).toHaveLength(3)
    await user.click(card.getByRole('button', { name: 'Launch Greenfield' }))
    expect(await screen.findByRole('heading', { name: 'Runs destination' })).toBeInTheDocument()
    const post = fn.mock.calls.find(([, i]) => i?.method === 'POST')!
    expect(post[0]).toBe('/api/agent-runs')
    expect(JSON.parse(post[1]!.body as string)).toEqual({ scenario: 'GREENFIELD' })
  })

  it('launch failure shows an alert and stays on the page', async () => {
    const user = userEvent.setup()
    const fn = setup()
    fn.mockImplementation(async (input, init) =>
      init?.method === 'POST'
        ? json({ error: { code: 'BAD', message: 'Launch failed' } }, 400)
        : String(input) === '/api/scenarios' ? json(scenarios) : json(overall),
    )
    await user.click(await screen.findByRole('button', { name: 'Launch Brownfield' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Launch failed')
  })

  it('selecting a run loads RunMetrics with the stage table', async () => {
    const user = userEvent.setup()
    setup()
    await user.selectOptions(await screen.findByLabelText('Run'), '7')
    const table = await screen.findByRole('table', { name: 'Stage latency' })
    const building = within(table).getByRole('row', { name: /Building/ })
    expect(within(building).getByText('2')).toBeInTheDocument()
    expect(building).toHaveTextContent('2.5 s')
    expect(within(await tile('Run MTTR')).getByText('500 ms')).toBeInTheDocument()
    expect(within(await tile('Approvals rejected')).getByText('1')).toBeInTheDocument()
    expect(within(await tile('End-to-end')).getByText('1.1 min')).toBeInTheDocument()
  })

  it('selects the run from the query string', async () => {
    setup({}, '/metrics?run=7')
    expect(await screen.findByRole('table', { name: 'Stage latency' })).toBeInTheDocument()
    expect(screen.getByLabelText('Run')).toHaveValue('7')
  })

  it('downloads the engineering summary as a markdown Blob, by keyboard', async () => {
    const user = userEvent.setup()
    const create = vi.fn(() => 'blob:x')
    const revoke = vi.fn()
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: create, revokeObjectURL: revoke }))
    setup({}, '/metrics?run=7')
    const btn = await screen.findByRole('button', { name: 'Download engineering summary' })
    btn.focus()
    await user.keyboard('{Enter}')
    await waitFor(() => expect(create).toHaveBeenCalled())
    const blob = (create.mock.calls[0] as unknown as [Blob])[0]
    expect(await blob.text()).toBe('# Summary\nAll good')
    expect(blob.type).toBe('text/markdown')
    expect(revoke).toHaveBeenCalled()
  })

  it('shows an alert when run metrics fail to load', async () => {
    setup({ '/api/agent-runs/7/metrics': () => json({ error: { code: 'X', message: 'Run metrics failed' } }, 500) }, '/metrics?run=7')
    expect(await screen.findByText('Run metrics failed')).toHaveAttribute('role', 'alert')
  })
})
