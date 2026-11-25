import { http, httpText, saveText, seg } from './http'
import type { RunDetail } from './runs'

export interface ScenarioPreset {
  id: 'GREENFIELD' | 'BROWNFIELD' | 'AMBIGUOUS'
  name: string
  description: string
  requirement: string
}

export interface OverallMetrics {
  runsTotal: number
  runsSucceeded: number
  runsFailed: number
  runsInProgress: number
  successRate: number | null
  retries: number
  rollbacks: number
  fallbacks: number
  failedStages: number
  blockedStages: number
  avgEndToEndMs: number | null
  p95StageLatencyMs: number | null
  mttrMs: number | null
}

export interface StageLatency {
  nodeId: string
  name: string
  attempts: number
  durationMs: number | null
}

export interface RunMetrics {
  runId: number
  status: string
  endToEndMs: number | null
  stagesTotal: number
  stagesSucceeded: number
  retries: number
  rollbacks: number
  fallbacks: number
  failedStages: number
  blockedStages: number
  waitingStages: number
  mttrMs: number | null
  approvals: { approved: number; rejected: number; pending: number }
  stageLatencies: StageLatency[]
}

export const getOverallMetrics = () => http<OverallMetrics>('/api/metrics')
export const getRunMetrics = (id: number) => http<RunMetrics>(`/api/agent-runs/${seg(id)}/metrics`)
export const listScenarios = () => http<ScenarioPreset[]>('/api/scenarios')

/** Create a run from a scenario preset; the server seeds the requirement. */
export const launchScenario = (scenario: ScenarioPreset['id']) =>
  http<RunDetail>('/api/agent-runs', { method: 'POST', body: JSON.stringify({ scenario }) })

export const getSummary = (id: number) => httpText(`/api/agent-runs/${seg(id)}/summary`)

export async function downloadSummary(id: number) {
  saveText(await getSummary(id), `run-${id}-summary.md`)
}
