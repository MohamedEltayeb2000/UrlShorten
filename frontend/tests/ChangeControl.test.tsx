// @req 001-FR-033
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ApprovalsPage from '../src/pages/ApprovalsPage'

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const high = {
  approvalId: 5,
  runId: 11,
  nodeId: 'IMPLEMENTATION',
  nodeName: 'Implementation',
  kind: 'HIGH_IMPACT_ACTION',
  reason: 'Patch proposal',
  artifactVersion: 1,
  riskClass: 'HIGH',
  proposedCommands: JSON.stringify(['mvn -q test']),
  proposedMigrations: JSON.stringify([
    { name: 'V2__add_table', sql: 'create table t (id int)', touchesNonTestData: false, destructive: false },
  ]),
  createdAt: '2026-11-24T10:00:00Z',
}

afterEach(() => vi.unstubAllGlobals())

function setup(decision?: Response) {
  const fn = vi.fn(async (_i: RequestInfo | URL, init?: RequestInit) =>
    init?.method === 'POST' ? (decision ?? json({ id: 5, nodeId: 'IMPLEMENTATION', status: 'APPROVED', actorId: 'alice', createdAt: 'x' })) : json([high]),
  )
  vi.stubGlobal('fetch', fn)
  render(
    <MemoryRouter>
      <ApprovalsPage />
    </MemoryRouter>,
  )
  return fn
}

const card = async () => within(await screen.findByRole('article', { name: /Run #11/ }))
const post = (fn: ReturnType<typeof setup>) => fn.mock.calls.find(([, i]) => i?.method === 'POST')

describe('change control on a HIGH-risk approval', () => {
  it('shows the risk class, proposed commands and migrations', async () => {
    setup()
    const c = await card()
    expect(c.getByText('Risk class: HIGH')).toBeInTheDocument()
    expect(c.getByText('mvn -q test')).toBeInTheDocument()
    expect(c.getByText(/V2__add_table/)).toBeInTheDocument()
    expect(c.getByLabelText('Change ticket')).toBeInTheDocument()
    expect(c.getByLabelText('Risk justification')).toBeInTheDocument()
  })

  it('refuses to approve without a valid ticket and justification and focuses the alert', async () => {
    const fn = setup()
    const c = await card()
    const user = userEvent.setup()
    await user.click(c.getByRole('button', { name: 'Approve' }))
    const alert = await c.findByRole('alert')
    expect(alert).toHaveTextContent(/change ticket/i)
    await waitFor(() => expect(alert).toHaveFocus())
    await user.type(c.getByLabelText('Change ticket'), 'nope')
    await user.click(c.getByRole('button', { name: 'Approve' }))
    expect(await c.findByRole('alert')).toHaveTextContent(/change ticket/i)
    await user.clear(c.getByLabelText('Change ticket'))
    await user.type(c.getByLabelText('Change ticket'), 'CHG-1042')
    await user.type(c.getByLabelText('Risk justification'), 'short')
    await user.click(c.getByRole('button', { name: 'Approve' }))
    expect(await c.findByRole('alert')).toHaveTextContent(/at least 10/i)
    expect(post(fn)).toBeUndefined()
  })

  it('sends the change record with the approval', async () => {
    const fn = setup()
    const c = await card()
    const user = userEvent.setup()
    await user.type(c.getByLabelText('Change ticket'), 'CHG-1042')
    await user.type(c.getByLabelText('Risk justification'), 'Reviewed the diff, test data only')
    await user.click(c.getByRole('button', { name: 'Approve' }))
    expect(await c.findByRole('status')).toHaveTextContent('Approved by alice')
    expect(JSON.parse(post(fn)![1]!.body as string)).toMatchObject({
      decision: 'APPROVE',
      changeTicket: 'CHG-1042',
      riskJustification: 'Reviewed the diff, test data only',
    })
  })

  it('does not ask for a change record to reject', async () => {
    const fn = setup(json({ id: 5, nodeId: 'IMPLEMENTATION', status: 'REJECTED', createdAt: 'x' }))
    const c = await card()
    const user = userEvent.setup()
    await user.type(c.getByLabelText(/Comment/), 'not safe')
    await user.click(c.getByRole('button', { name: 'Reject' }))
    expect(await c.findByText(/Blocked - feedback recorded/)).toBeInTheDocument()
    expect(JSON.parse(post(fn)![1]!.body as string).changeTicket).toBeUndefined()
  })

  it('shows the server CHANGE_RECORD_REQUIRED error in the focused alert', async () => {
    setup(json({ error: { code: 'CHANGE_RECORD_REQUIRED', message: 'A change ticket is required' } }, 400))
    const c = await card()
    const user = userEvent.setup()
    await user.type(c.getByLabelText('Change ticket'), 'CHG-1042')
    await user.type(c.getByLabelText('Risk justification'), 'Reviewed the diff, test data only')
    await user.click(c.getByRole('button', { name: 'Approve' }))
    const alert = await c.findByRole('alert')
    expect(alert).toHaveTextContent('A change ticket is required')
    await waitFor(() => expect(alert).toHaveFocus())
  })
})
