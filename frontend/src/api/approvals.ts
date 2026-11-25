import { http } from './http'

export type ApprovalKind = 'CLARIFICATION' | 'HIGH_IMPACT_ACTION' | 'HELD_OUTPUT'

export interface PendingApproval {
  approvalId: number
  runId: number
  nodeId: string
  nodeName: string
  kind: ApprovalKind
  reason: string
  artifactType?: string
  artifactVersion?: number
  summary?: string
  openQuestions?: string[]
  assumptions?: string[]
  policyReason?: string
  createdAt: string
}

export interface ApprovalView {
  id: number
  nodeId: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  actorId?: string
  comment?: string
  policyReason?: string
  artifactVersion?: number
  createdAt: string
  decidedAt?: string
}

export interface DecisionBody {
  nodeId: string
  decision: 'APPROVE' | 'REJECT'
  comment: string
  artifactVersion?: number
}

export const listPendingApprovals = () => http<PendingApproval[]>('/api/approvals/pending')

export const listRunApprovals = (runId: number) => http<ApprovalView[]>(`/api/agent-runs/${runId}/approvals`)

export const decide = (runId: number, body: DecisionBody, user = 'demo-user') =>
  http<ApprovalView>(`/api/agent-runs/${runId}/approvals`, {
    method: 'POST',
    headers: { 'X-Demo-User': user.trim() || 'demo-user' },
    body: JSON.stringify(body),
  })
