// @req 001-FR-032
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ArtifactsPage from '../src/pages/ArtifactsPage'

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const art = (over: Record<string, unknown>) => ({
  id: 1,
  nodeId: 'design',
  type: 'DESIGN',
  version: 1,
  status: 'CURRENT',
  checksum: 'abcdef0123456789ffff',
  parentIds: [],
  inputRefs: [],
  createdBy: 'agent-1',
  createdAt: '2026-11-24T10:00:00Z',
  content: 'original design',
  ...over,
})

afterEach(() => vi.unstubAllGlobals())

function setup(putResponse: () => Response) {
  const calls: { url: string; init?: RequestInit }[] = []
  let list = [art({})]
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      calls.push({ url, init })
      if (init?.method === 'PUT') {
        const res = putResponse()
        if (res.ok) list = [art({ id: 1, status: 'SUPERSEDED' }), art({ id: 2, version: 2, content: 'new design' })]
        return res
      }
      if (url.includes('/events')) return json({ items: [], page: 0, size: 10, totalItems: 0, totalPages: 0 })
      return json(list)
    }),
  )
  render(
    <MemoryRouter initialEntries={['/runs/9/artifacts']}>
      <Routes>
        <Route path="/runs/:id/artifacts" element={<ArtifactsPage />} />
      </Routes>
    </MemoryRouter>,
  )
  return calls
}

describe('artifact edit', () => {
  it('opens a prefilled form with the downstream note and is keyboard reachable', async () => {
    setup(() => json({}))
    const edit = await screen.findByRole('button', { name: 'Edit this version' })
    await userEvent.tab()
    edit.focus()
    expect(edit).toHaveFocus()
    await userEvent.keyboard('{Enter}')
    expect(screen.getByLabelText('Edited content of DESIGN')).toHaveValue('original design')
    expect(screen.getByText('Downstream stages will be marked stale and re-run automatically')).toBeInTheDocument()
    expect(screen.getByLabelText('Comment (required)')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByLabelText('Edited content of DESIGN')).not.toBeInTheDocument()
  })

  it('requires a comment, then saves and refreshes the list', async () => {
    const calls = setup(() => json(art({ id: 2, version: 2 })))
    await userEvent.click(await screen.findByRole('button', { name: 'Edit this version' }))
    const text = screen.getByLabelText('Edited content of DESIGN')
    await userEvent.clear(text)
    await userEvent.type(text, 'new design')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('A comment is required')
    expect(calls.some((c) => c.init?.method === 'PUT')).toBe(false)

    await userEvent.type(screen.getByLabelText('Comment (required)'), 'clearer wording')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(screen.getByLabelText('Content of DESIGN v2')).toHaveTextContent('new design'))
    const put = calls.find((c) => c.init?.method === 'PUT')!
    expect(put.url).toBe('/api/agent-runs/9/artifacts')
    expect((put.init?.headers as Record<string, string>)['X-Demo-User']).toBe('demo-user')
    expect(JSON.parse(String(put.init?.body))).toEqual({
      nodeId: 'design',
      artifactType: 'DESIGN',
      content: 'new design',
      comment: 'clearer wording',
    })
    expect(screen.queryByLabelText('Edited content of DESIGN')).not.toBeInTheDocument()
  })

  it('shows the server error in an alert that receives focus', async () => {
    setup(() => json({ error: { code: 'POLICY_DENIED', message: 'The edited content violates policy' } }, 400))
    await userEvent.click(await screen.findByRole('button', { name: 'Edit this version' }))
    await userEvent.type(screen.getByLabelText('Comment (required)'), 'why')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('violates policy')
    await waitFor(() => expect(alert).toHaveFocus())
    expect(within(screen.getByRole('form', { name: /Edit DESIGN/ })).getByRole('button', { name: 'Save' })).toBeEnabled()
  })
})
