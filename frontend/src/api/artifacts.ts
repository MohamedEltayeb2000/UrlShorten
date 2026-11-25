import { http, seg, int } from './http'
import type { Page } from './links'

export type ArtifactStatus = 'CURRENT' | 'STALE' | 'SUPERSEDED'

export interface ArtifactView {
  id: number
  nodeId: string
  type: string
  version: number
  status: ArtifactStatus
  checksum: string
  parentIds: number[]
  inputRefs: string[]
  createdBy: string
  createdAt: string
  content: string
}

export interface AuditEventView {
  id: number
  actorType: string
  actorId: string
  eventType: string
  occurredAt: string
  details?: string
  correlationId?: string
}

export const listArtifacts = (runId: string) =>
  http<ArtifactView[]>(`/api/agent-runs/${seg(runId)}/artifacts`)

export const listEvents = (runId: string, page = 0, size = 10) =>
  http<Page<AuditEventView>>(`/api/agent-runs/${seg(runId)}/events?page=${int(page)}&size=${int(size)}`)

export const editArtifact = (
  runId: string,
  body: { nodeId: string; artifactType: string; content: string; comment: string },
  user = 'demo-user',
) =>
  http<ArtifactView>(`/api/agent-runs/${seg(runId)}/artifacts`, {
    method: 'PUT',
    headers: { 'X-Demo-User': user.trim() || 'demo-user' },
    body: JSON.stringify(body),
  })

export const rollbackArtifact = (runId: string, nodeId: string, artifactType: string, version: number) =>
  http<ArtifactView>(`/api/agent-runs/${seg(runId)}/rollback`, {
    method: 'POST',
    body: JSON.stringify({ nodeId, artifactType, version }),
  })
