// @req 001-FR-031
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import type { RunNode } from '../src/api/runs'
import StageList from '../src/components/StageList'

const node = (id: string, status: string, gates?: RunNode['gates']): RunNode => ({
  id,
  name: id,
  status,
  level: 0,
  dependsOn: [],
  attempts: 0,
  gates,
})

const renderList = (nodes: RunNode[]) =>
  render(
    <MemoryRouter>
      <StageList nodes={nodes} />
    </MemoryRouter>,
  )

describe('StageList gates', () => {
  it('states the entry and exit gate outcomes as text', () => {
    renderList([
      node('Plan', 'SUCCEEDED', {
        entry: { passed: true, checks: ['all prerequisites succeeded: passed'] },
        exit: { passed: false, checks: ['output stored as an artifact: failed'] },
      }),
    ])
    expect(screen.getByText('Entry gate: passed')).toBeInTheDocument()
    expect(screen.getByText('Exit gate: failed')).toBeInTheDocument()
    expect(screen.getByText('Exit gate: failed')).toHaveAttribute('title', 'output stored as an artifact: failed')
  })

  it('shows not evaluated when a gate has no result yet', () => {
    renderList([node('Plan', 'PENDING', { entry: { checks: ['all prerequisites succeeded'] }, exit: { checks: [] } })])
    expect(screen.getByText('Entry gate: not evaluated')).toBeInTheDocument()
    expect(screen.getByText('Exit gate: not evaluated')).toBeInTheDocument()
  })

  it('omits the gate lines for a stage without gate data', () => {
    renderList([node('Plan', 'PENDING')])
    expect(screen.queryByText(/Entry gate/)).not.toBeInTheDocument()
  })
})
