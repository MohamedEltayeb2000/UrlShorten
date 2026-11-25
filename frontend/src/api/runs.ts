import { http, seg, int } from './http'
import type { Page } from './links'

export type Scenario = 'GREENFIELD' | 'BROWNFIELD' | 'AMBIGUOUS'

export interface RunSummary {
  id: number
  scenario?: string
  requirement: string
  status: string
  createdAt: string
}

/** One stage gate; `passed` is absent when the gate has not been evaluated yet. */
export interface Gate {
  passed?: boolean
  checks: string[]
}

export interface StageGatesView {
  entry: Gate
  exit: Gate
}

export interface RunNode {
  id: string
  name: string
  status: string
  dependsOn: string[]
  level: number
  attempts: number
  startedAt?: string
  completedAt?: string
  errorSummary?: string
  gates?: StageGatesView
}

export interface RunDetail extends RunSummary {
  planVersion: number
  startedAt?: string
  completedAt?: string
  nodes: RunNode[]
}

export const createRun = (requirement: string, scenario?: Scenario) =>
  http<RunDetail>('/api/agent-runs', { method: 'POST', body: JSON.stringify({ requirement, scenario }) })

export const listRuns = (page = 0, size = 20) => http<Page<RunSummary>>(`/api/agent-runs?page=${int(page)}&size=${int(size)}`)

export const getRun = (id: number) => http<RunDetail>(`/api/agent-runs/${seg(id)}`)

export const startRun = (id: number) => http<RunDetail>(`/api/agent-runs/${seg(id)}/start`, { method: 'POST' })

export const pauseRun = (id: number) => http<RunDetail>(`/api/agent-runs/${seg(id)}/pause`, { method: 'POST' })

export const retryNode = (runId: number, nodeId: string) =>
  http<RunDetail>(`/api/agent-runs/${seg(runId)}/nodes/${seg(nodeId)}/retry`, { method: 'POST' })

export const replanRun = (runId: number, nodeId: string, requirement?: string) =>
  http<RunDetail>(`/api/agent-runs/${seg(runId)}/replan`, {
    method: 'POST',
    body: JSON.stringify({ nodeId, requirement: requirement || undefined }),
  })
