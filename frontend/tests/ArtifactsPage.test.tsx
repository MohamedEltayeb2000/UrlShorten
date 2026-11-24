// @req 001-FR-021
// @req 002-FR-017
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ArtifactsPage from '../src/pages/ArtifactsPage'

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const art = (over: Record<string, unknown>) => ({
  id: 1,
  nodeId: 'requirements',
  type: 'REQUIREMENT',
  version: 1,
  status: 'CURRENT',
  checksum: 'abcdef0123456789ffff',
  parentIds: [],
  inputRefs: [],
  createdBy: 'agent-1',
  createdAt: '2026-11-24T10:00:00Z',
  content: 'line one',
  ...over,
})

const artifacts = [
  art({ id: 1, version: 1, status: 'SUPERSEDED', content: 'alpha\nbeta\ngamma' }),
  art({ id: 2, version: 2, content: 'alpha\ndelta\ngamma', inputRefs: ['idea.md'], parentIds: [7, 8] }),
  art({ id: 3, nodeId: 'coding', type: 'CODE_PROPOSAL', status: 'STALE', content: 'print(1)' }),
  art({ id: 4, nodeId: 'design', type: 'DESIGN', content: 'diagram' }),
]

const events = (page: number, totalPages = 2) => ({
  items: [
    {
      id: page + 1,
      actorType: 'AGENT',
      actorId: `actor${page}`,
      eventType: `EVT_${page}`,
      occurredAt: '2026-11-24T10:00:00Z',
      details: `detail ${page}`,
      correlationId: `corr-${page}`,
    },
  ],
  page,
  size: 10,
  totalItems: 2,
  totalPages,
})

afterEach(() => vi.unstubAllGlobals())

function setup(opts: { list?: unknown; fail?: boolean; urls?: string[] } = {}) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      opts.urls?.push(url)
      if (url.includes('/artifacts')) {
        if (opts.fail) return json({ error: { code: 'NOT_FOUND', message: 'Run 9 not found' } }, 404)
        return json(opts.list ?? artifacts)
      }
      const page = Number(/page=(\d+)/.exec(url)?.[1] ?? 0)
      return json(events(page))
    }),
  )
  render(
    <MemoryRouter initialEntries={['/runs/9/artifacts']}>
      <Routes>
        <Route path="/runs/:id/artifacts" element={<ArtifactsPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('artifacts page', () => {
  it('groups artifacts by stage', async () => {
    setup()
    expect(await screen.findByRole('heading', { name: 'Stage: requirements' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Stage: coding' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Stage: design' })).toBeInTheDocument()
  })

  it('shows status as text and the 12-char checksum', async () => {
    setup()
    await screen.findByText('Stage: coding')
    const coding = screen.getByRole('article', { name: 'CODE_PROPOSAL' })
    expect(within(coding).getByText('STALE', { selector: '.status' })).toBeInTheDocument()
    expect(within(coding).getByText('abcdef012345')).toBeInTheDocument()
    const req = screen.getByRole('article', { name: 'REQUIREMENT' })
    expect(within(req).getByText('CURRENT', { selector: '.status' })).toHaveClass('status-current')
  })

  it('switches content with the version selector and shows provenance', async () => {
    setup()
    const select = await screen.findByLabelText('Version for requirements REQUIREMENT')
    const req = screen.getByRole('article', { name: 'REQUIREMENT' })
    expect(within(req).getByText('inputs: idea.md')).toBeInTheDocument()
    expect(within(req).getByText('parents: #7, #8')).toBeInTheDocument()
    expect(within(req).getByLabelText('Content of REQUIREMENT v2')).toHaveTextContent('delta')
    await userEvent.selectOptions(select, 'v1 (SUPERSEDED)')
    expect(within(req).getByLabelText('Content of REQUIREMENT v1')).toHaveTextContent('beta')
    expect(within(req).getByText('SUPERSEDED', { selector: '.status' })).toBeInTheDocument()
  })

  it('shows +/- diff lines between selected and current version', async () => {
    setup()
    const select = await screen.findByLabelText('Version for requirements REQUIREMENT')
    expect(screen.getByText(/pick an older version to compare/)).toBeInTheDocument()
    await userEvent.selectOptions(select, 'v1 (SUPERSEDED)')
    const diff = screen.getByRole('region', { name: /Diff of REQUIREMENT v1 against v2/ })
    expect(diff.textContent).toContain('- beta')
    expect(diff.textContent).toContain('+ delta')
    expect(diff.textContent).toContain('  alpha')
  })

  it('shows the proposal banner only for CODE_PROPOSAL', async () => {
    setup()
    await screen.findByText('Stage: coding')
    expect(screen.getAllByText('Proposal - not applied')).toHaveLength(1)
    const coding = screen.getByRole('article', { name: 'CODE_PROPOSAL' })
    expect(within(coding).getByText('Proposal - not applied')).toBeInTheDocument()
  })

  it('shows the empty state', async () => {
    setup({ list: [] })
    expect(await screen.findByText('No artifacts yet')).toBeInTheDocument()
  })

  it('shows errors in an alert and moves focus to it', async () => {
    setup({ fail: true })
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Run 9 not found')
    expect(alert).toHaveFocus()
  })
})

describe('audit timeline', () => {
  it('lists events and pages with Next/Previous', async () => {
    const urls: string[] = []
    setup({ urls })
    expect(await screen.findByText(/EVT_0/)).toBeInTheDocument()
    expect(screen.getByText(/corr-0/)).toBeInTheDocument()
    expect(screen.getByText(/detail 0/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Next' }))
    expect(await screen.findByText(/EVT_1/)).toBeInTheDocument()
    expect(urls.some((u) => u.includes('/api/agent-runs/9/events?page=1'))).toBe(true)
    await userEvent.click(screen.getByRole('button', { name: 'Previous' }))
    expect(await screen.findByText(/EVT_0/)).toBeInTheDocument()
  })

  it('makes the version selector and paging buttons keyboard reachable', async () => {
    setup()
    const select = await screen.findByLabelText('Version for requirements REQUIREMENT')
    select.focus()
    expect(select).toHaveFocus()
    const next = await screen.findByRole('button', { name: 'Next' })
    next.focus()
    expect(next).toHaveFocus()
    await userEvent.keyboard('{Enter}')
    expect(await screen.findByText(/EVT_1/)).toBeInTheDocument()
  })
})
