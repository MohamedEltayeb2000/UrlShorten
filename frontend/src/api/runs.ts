import { http } from './http'
import type { Page } from './links'

export type Scenario = 'GREENFIELD' | 'BROWNFIELD' | 'AMBIGUOUS'

export interface RunSummary {
  id: number
  scenario?: string
  requirement: string
  status: string
  createdAt: string
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
}

export interface RunDetail extends RunSummary {
  planVersion: number
  startedAt?: string
  completedAt?: string
  nodes: RunNode[]
}

export const createRun = (requirement: string, scenario?: Scenario) =>
  http<RunDetail>('/api/agent-runs', { method: 'POST', body: JSON.stringify({ requirement, scenario }) })

export const listRuns = (page = 0, size = 20) => http<Page<RunSummary>>(`/api/agent-runs?page=${page}&size=${size}`)

export const getRun = (id: number) => http<RunDetail>(`/api/agent-runs/${id}`)

export const startRun = (id: number) => http<RunDetail>(`/api/agent-runs/${id}/start`, { method: 'POST' })

export const pauseRun = (id: number) => http<RunDetail>(`/api/agent-runs/${id}/pause`, { method: 'POST' })

export const retryNode = (runId: number, nodeId: string) =>
  http<RunDetail>(`/api/agent-runs/${runId}/nodes/${encodeURIComponent(nodeId)}/retry`, { method: 'POST' })

export const replanRun = (runId: number, nodeId: string, requirement?: string) =>
  http<RunDetail>(`/api/agent-runs/${runId}/replan`, {
    method: 'POST',
    body: JSON.stringify({ nodeId, requirement: requirement || undefined }),
  })
