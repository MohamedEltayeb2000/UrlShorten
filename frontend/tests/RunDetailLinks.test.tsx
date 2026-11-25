// @req 001-FR-021
// @req 002-FR-017
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import RunDetailPanel from '../src/components/RunDetailPanel'

const run = {
  id: 7,
  requirement: 'Build links',
  status: 'SUCCEEDED',
  createdAt: '2026-11-24T12:00:00Z',
  planVersion: 1,
  nodes: [],
}

describe('run detail links', () => {
  it('links to the artifacts and audit trail and to the metrics of the run', () => {
    render(
      <MemoryRouter>
        <RunDetailPanel run={run} busy={false} onStart={() => {}} onPause={() => {}} onRetry={() => {}} onReplan={() => {}} />
      </MemoryRouter>,
    )
    expect(screen.getByRole('link', { name: /View artifacts and audit trail/ })).toHaveAttribute('href', '/runs/7/artifacts')
    expect(screen.getByRole('link', { name: 'View metrics' })).toHaveAttribute('href', '/metrics?run=7')
  })
})
