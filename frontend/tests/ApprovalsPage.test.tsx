// @req 001-FR-021
// @req 001-FR-011
// @req 001-FR-012
// @req 002-FR-017
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ApprovalsPage from '../src/pages/ApprovalsPage'

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const pending = [
  {
    approvalId: 1,
    runId: 7,
    nodeId: 'clarify',
    nodeName: 'Clarification',
    kind: 'CLARIFICATION',
    reason: 'Requirement is ambiguous',
    artifactType: 'CLARIFICATION',
    artifactVersion: 2,
    summary: 'Needs answers',
    openQuestions: ['Which database?'],
    assumptions: ['Single region'],
    policyReason: 'Ambiguous scenario',
    riskClass: 'MEDIUM',
    createdAt: '2026-11-24T10:00:00Z',
  },
  {
    approvalId: 2,
    runId: 8,
    nodeId: 'deploy',
    nodeName: 'Deploy',
    kind: 'HIGH_IMPACT_ACTION',
    reason: 'Deploys to prod',
    riskClass: 'HIGH',
    createdAt: '2026-11-24T11:00:00Z',
  },
]

afterEach(() => vi.unstubAllGlobals())

const approved = { id: 1, nodeId: 'clarify', status: 'APPROVED', actorId: 'alice', createdAt: 'x' }

function setup(opts: { list?: unknown; listStatus?: number; decision?: Response } = {}) {
  const fn = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
    if (init?.method === 'POST') return opts.decision ?? json(approved)
    return json(opts.list ?? pending, opts.listStatus ?? 200)
  })
  vi.stubGlobal('fetch', fn)
  render(
    <MemoryRouter>
      <ApprovalsPage />
    </MemoryRouter>,
  )
  return fn
}

const card = async (name: RegExp) => within(await screen.findByRole('article', { name }))
const post = (fn: ReturnType<typeof setup>) => fn.mock.calls.find(([, i]) => i?.method === 'POST')

describe('ApprovalsPage', () => {
  it('shows loading then the queue with context', async () => {
    setup()
    expect(screen.getByText('Loading approvals…')).toBeInTheDocument()
    const c = await card(/Run #7/)
    expect(c.getByText(/Requirement is ambiguous/)).toBeInTheDocument()
    expect(c.getByText('CLARIFICATION', { selector: 'span' })).toBeInTheDocument()
    expect(c.getByText(/Artifact: CLARIFICATION v2/)).toBeInTheDocument()
    expect(c.getByText(/Needs answers/)).toBeInTheDocument()
    expect(c.getByText('Which database?')).toBeInTheDocument()
    expect(c.getByText('Single region')).toBeInTheDocument()
    expect(c.getByText(/Ambiguous scenario/)).toBeInTheDocument()
    expect(c.getByText('Risk class: MEDIUM')).toBeInTheDocument()
    expect(c.queryByLabelText('Change ticket')).not.toBeInTheDocument()
    expect(c.getByRole('link', { name: /artifacts for run 7/i })).toHaveAttribute('href', '/runs/7/artifacts')
    expect(screen.getByRole('article', { name: /Run #8/ })).toBeInTheDocument()
  })

  it('has labeled fields', async () => {
    setup()
    const c = await card(/Run #7/)
    expect(c.getByLabelText('Acting as')).toHaveValue('demo-user')
    expect(c.getByLabelText(/Comment/)).toBeInTheDocument()
  })

  it('shows the empty state', async () => {
    setup({ list: [] })
    expect(await screen.findByText('Nothing is waiting for approval')).toBeInTheDocument()
  })

  it('shows an error alert and focuses it', async () => {
    setup({ list: { error: { code: 'X', message: 'boom' } }, listStatus: 500 })
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('boom')
    await waitFor(() => expect(alert).toHaveFocus())
  })

  it('approves with body and X-Demo-User header', async () => {
    const fn = setup()
    const c = await card(/Run #7/)
    const user = userEvent.setup()
    await user.clear(c.getByLabelText('Acting as'))
    await user.type(c.getByLabelText('Acting as'), 'alice')
    await user.type(c.getByLabelText(/Comment/), 'looks good')
    await user.click(c.getByRole('button', { name: 'Approve' }))
    expect(await c.findByRole('status')).toHaveTextContent('Approved by alice')
    const p = post(fn)!
    expect(String(p[0])).toBe('/api/agent-runs/7/approvals')
    expect(JSON.parse(p[1]!.body as string)).toEqual({
      nodeId: 'clarify',
      decision: 'APPROVE',
      comment: 'looks good',
      artifactVersion: 2,
    })
    expect((p[1]!.headers as Record<string, string>)['X-Demo-User']).toBe('alice')
  })

  it('requires a comment to reject, then records blocked feedback', async () => {
    const fn = setup({
      decision: json({ id: 1, nodeId: 'clarify', status: 'REJECTED', actorId: 'demo-user', comment: 'no', createdAt: 'x' }),
    })
    const c = await card(/Run #7/)
    const user = userEvent.setup()
    await user.click(c.getByRole('button', { name: 'Reject' }))
    expect(await c.findByRole('alert')).toHaveTextContent(/comment is required/i)
    expect(post(fn)).toBeUndefined()
    await user.type(c.getByLabelText(/Comment/), 'no')
    await user.click(c.getByRole('button', { name: 'Reject' }))
    expect(await c.findByText(/Blocked - feedback recorded/)).toBeInTheDocument()
    const p = post(fn)!
    expect(JSON.parse(p[1]!.body as string).decision).toBe('REJECT')
    expect((p[1]!.headers as Record<string, string>)['X-Demo-User']).toBe('demo-user')
  })

  it('shows a clear message on APPROVAL_FORBIDDEN', async () => {
    setup({ decision: json({ error: { code: 'APPROVAL_FORBIDDEN', message: 'author cannot approve' } }, 403) })
    const c = await card(/Run #7/)
    await userEvent.setup().click(c.getByRole('button', { name: 'Approve' }))
    const alert = await c.findByRole('alert')
    expect(alert).toHaveTextContent(/cannot approve their own output/)
    await waitFor(() => expect(alert).toHaveFocus())
  })

  it('is operable by keyboard', async () => {
    const fn = setup()
    const c = await card(/Run #7/)
    const user = userEvent.setup()
    c.getByLabelText('Acting as').focus()
    await user.tab()
    expect(c.getByLabelText(/Comment/)).toHaveFocus()
    await user.tab()
    expect(c.getByRole('button', { name: 'Approve' })).toHaveFocus()
    await user.keyboard('{Enter}')
    await c.findByRole('status')
    expect(post(fn)).toBeDefined()
  })
})
