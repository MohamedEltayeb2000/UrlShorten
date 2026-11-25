// @req 001-FR-021
// @req 002-FR-017
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ArtifactsPage from '../src/pages/ArtifactsPage'
import RunsPage from '../src/pages/RunsPage'

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const node = (id: string, name: string, level: number, status: string) => ({
  id,
  name,
  status,
  level,
  dependsOn: [],
  attempts: 1,
})
const run = (planVersion: number, nodes: unknown[]) => ({
  id: 7,
  requirement: 'Build',
  status: 'PAUSED',
  createdAt: '2026-01-01T00:00:00Z',
  planVersion,
  nodes,
})
const nodes = [
  node('a', 'Plan', 0, 'SUCCEEDED'),
  node('b', 'Code', 1, 'FAILED'),
  node('c', 'Review', 2, 'BLOCKED'),
  node('d', 'Test', 3, 'WAITING_FOR_APPROVAL'),
  node('e', 'Docs', 4, 'RUNNING'),
]

afterEach(() => vi.unstubAllGlobals())

function mock(handlers: Record<string, () => Response>) {
  const fn = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${String(input).split('?')[0]}`
    const h = handlers[key]
    if (!h) throw new Error(`unexpected ${key}`)
    return h()
  })
  vi.stubGlobal('fetch', fn)
  return fn
}

async function openRun() {
  render(
    <MemoryRouter>
      <RunsPage />
    </MemoryRouter>,
  )
  await userEvent.setup().click(await screen.findByRole('button', { name: 'Open run 7' }))
}

const list = {
  'GET /api/agent-runs': () =>
    json({
      items: [{ id: 7, requirement: 'Build', status: 'PAUSED', createdAt: 'x' }],
      page: 0,
      size: 20,
      totalItems: 1,
      totalPages: 1,
    }),
}

describe('run controls', () => {
  it('shows Retry only for FAILED and BLOCKED, and posts retry', async () => {
    const fn = mock({
      ...list,
      'GET /api/agent-runs/7': () => json(run(1, nodes)),
      'POST /api/agent-runs/7/nodes/b/retry': () => json(run(1, nodes)),
    })
    await openRun()
    const retries = await screen.findAllByRole('button', { name: /^Retry/ })
    expect(retries.map((b) => b.getAttribute('aria-label'))).toEqual(['Retry Code', 'Retry Review'])
    await userEvent.setup().click(retries[0])
    expect(fn.mock.calls.some(([u, i]) => String(u).endsWith('/nodes/b/retry') && i?.method === 'POST')).toBe(true)
  })

  it('links WAITING_FOR_APPROVAL stages to /approvals and shows planVersion', async () => {
    mock({ ...list, 'GET /api/agent-runs/7': () => json(run(4, nodes)) })
    await openRun()
    expect(await screen.findByRole('link', { name: /Approvals/ })).toHaveAttribute('href', '/approvals')
    expect(screen.getByText(/Plan version:/)).toHaveTextContent('Plan version: 4')
  })

  it('re-plans from a stage with the changed requirement and shows the new plan version', async () => {
    const fn = mock({
      ...list,
      'GET /api/agent-runs/7': () => json(run(1, nodes)),
      'POST /api/agent-runs/7/replan': () =>
        json(run(2, nodes.map((n) => ({ ...(n as object), status: 'PENDING' })))),
    })
    await openRun()
    const user = userEvent.setup()
    await user.click((await screen.findAllByRole('button', { name: 'Re-plan from this stage' }))[1])
    expect(screen.getByText(/marks downstream stages stale and re-runs only the affected ones/)).toBeInTheDocument()
    await user.type(screen.getByLabelText(/Changed requirement/), 'use postgres')
    await user.click(screen.getByRole('button', { name: 'Re-plan Code' }))
    const p = fn.mock.calls.find(([, i]) => i?.method === 'POST')!
    expect(String(p[0])).toBe('/api/agent-runs/7/replan')
    expect(JSON.parse(p[1]!.body as string)).toEqual({ nodeId: 'b', requirement: 'use postgres' })
    expect(await screen.findByText(/Plan version: 2/)).toBeInTheDocument()
  })
})

describe('artifact rollback', () => {
  const art = (id: number, version: number, status: string, content: string) => ({
    id,
    nodeId: 'req',
    type: 'REQUIREMENT',
    version,
    status,
    checksum: 'abcdef0123456789',
    parentIds: [],
    inputRefs: [],
    createdBy: 'a',
    createdAt: '2026-11-24T10:00:00Z',
    content,
  })

  it('restores a superseded version after confirmation and refreshes', async () => {
    let restored = false
    const fn = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/rollback')) {
        restored = true
        return json(art(3, 3, 'CURRENT', 'one'))
      }
      if (url.includes('/events')) return json({ items: [], page: 0, size: 10, totalItems: 0, totalPages: 0 })
      return json(
        restored
          ? [art(1, 1, 'SUPERSEDED', 'one'), art(2, 2, 'SUPERSEDED', 'two'), art(3, 3, 'CURRENT', 'one')]
          : [art(1, 1, 'SUPERSEDED', 'one'), art(2, 2, 'CURRENT', 'two')],
      )
    })
    vi.stubGlobal('fetch', fn)
    render(
      <MemoryRouter initialEntries={['/runs/7/artifacts']}>
        <Routes>
          <Route path="/runs/:id/artifacts" element={<ArtifactsPage />} />
        </Routes>
      </MemoryRouter>,
    )
    const user = userEvent.setup()
    const article = within(await screen.findByRole('article'))
    expect(article.queryByRole('button', { name: 'Restore this version' })).toBeNull()
    await user.selectOptions(article.getByLabelText(/Version for/), '1')
    await user.click(article.getByRole('button', { name: 'Restore this version' }))
    expect(article.getByText(/Restoring keeps all history/)).toBeInTheDocument()
    await user.click(article.getByRole('button', { name: 'Confirm restore' }))
    const call = fn.mock.calls.find(([u]) => String(u).endsWith('/rollback'))!
    expect(JSON.parse((call as unknown as [string, RequestInit])[1].body as string)).toEqual({
      nodeId: 'req',
      artifactType: 'REQUIREMENT',
      version: 1,
    })
    expect(await screen.findByText('v3 (CURRENT)')).toBeInTheDocument()
  })
})
