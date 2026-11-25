import { http, seg } from './http'

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
  riskClass?: RiskClass
  changeTicket?: string
  riskJustification?: string
  /** JSON text: string[] */
  proposedCommands?: string
  /** JSON text: ProposedMigration[] */
  proposedMigrations?: string
}

export type RiskClass = 'LOW' | 'MEDIUM' | 'HIGH'

export interface ProposedMigration {
  name: string
  sql?: string
  touchesNonTestData?: boolean
  destructive?: boolean
}

export interface ApprovalView {
  riskClass?: RiskClass
  changeTicket?: string
  riskJustification?: string
  proposedCommands?: string
  proposedMigrations?: string
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
  changeTicket?: string
  riskJustification?: string
}

export const CHANGE_TICKET = /^[A-Za-z][A-Za-z0-9]*-[0-9]{1,8}$/

export function parseJsonList<T>(text?: string): T[] {
  if (!text) return []
  try {
    const v: unknown = JSON.parse(text)
    return Array.isArray(v) ? (v as T[]) : []
  } catch {
    return []
  }
}

export const listPendingApprovals = () => http<PendingApproval[]>('/api/approvals/pending')

export const listRunApprovals = (runId: number) => http<ApprovalView[]>(`/api/agent-runs/${seg(runId)}/approvals`)

export const decide = (runId: number, body: DecisionBody, user = 'demo-user') =>
  http<ApprovalView>(`/api/agent-runs/${seg(runId)}/approvals`, {
    method: 'POST',
    headers: { 'X-Demo-User': user.trim() || 'demo-user' },
    body: JSON.stringify(body),
  })
