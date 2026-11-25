import { useState } from 'react'
import { ArtifactView, rollbackArtifact } from '../api/artifacts'
import VersionDiff from './VersionDiff'

/** One (stage, type) group: version selector, provenance, content, proposal banner and diff vs current. */
export default function ArtifactViewer({
  versions,
  runId,
  onChanged,
}: {
  versions: ArtifactView[]
  runId?: string
  onChanged?: () => void
}) {
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)
  const sorted = [...versions].sort((a, b) => a.version - b.version)
  const current = sorted.find((v) => v.status === 'CURRENT') ?? sorted[sorted.length - 1]
  const [selectedId, setSelectedId] = useState(current.id)
  const sel = sorted.find((v) => v.id === selectedId) ?? current
  const base = `${sel.nodeId}-${sel.type}`
  const selectId = `ver-${base}`

  async function restore() {
    setBusy(true)
    setErr(null)
    try {
      await rollbackArtifact(runId as string, sel.nodeId, sel.type, sel.version)
      setConfirming(false)
      onChanged?.()
    } catch (e) {
      setErr((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <article className="artifact" aria-labelledby={`h-${base}`}>
      <h4 id={`h-${base}`}>{sel.type}</h4>
      {sel.type === 'CODE_PROPOSAL' && (
        <p className="proposal-banner" role="note">
          Proposal - not applied
        </p>
      )}
      <label htmlFor={selectId}>
        Version for {sel.nodeId} {sel.type}
      </label>{' '}
      <select id={selectId} value={sel.id} onChange={(e) => setSelectedId(Number(e.target.value))}>
        {sorted.map((v) => (
          <option key={v.id} value={v.id}>
            v{v.version} ({v.status})
          </option>
        ))}
      </select>
      <dl className="provenance">
        <dt>Version</dt>
        <dd>{sel.version}</dd>
        <dt>Status</dt>
        <dd>
          <span className={`status status-${sel.status.toLowerCase()}`}>{sel.status}</span>
        </dd>
        <dt>Checksum</dt>
        <dd>{sel.checksum.slice(0, 12)}</dd>
        <dt>Created by</dt>
        <dd>
          {sel.createdBy} at {new Date(sel.createdAt).toLocaleString()}
        </dd>
        <dt>Inputs</dt>
        <dd>inputs: {sel.inputRefs.length ? sel.inputRefs.join(', ') : 'none'}</dd>
        <dt>Parents</dt>
        <dd>parents: {sel.parentIds.length ? sel.parentIds.map((p) => `#${p}`).join(', ') : 'none'}</dd>
      </dl>
      {sel.status === 'SUPERSEDED' && runId && (
        <div className="restore">
          {!confirming ? (
            <button type="button" onClick={() => setConfirming(true)}>
              Restore this version
            </button>
          ) : (
            <div className="restore-confirm" role="group" aria-label={`Confirm restore of v${sel.version}`}>
              <p>Restore v{sel.version} as the current version? Restoring keeps all history.</p>
              <button type="button" disabled={busy} onClick={() => void restore()}>
                Confirm restore
              </button>{' '}
              <button type="button" disabled={busy} onClick={() => setConfirming(false)}>
                Cancel
              </button>
            </div>
          )}
          {err && (
            <p role="alert" className="error">
              {err}
            </p>
          )}
        </div>
      )}
      <pre className="content" tabIndex={0} aria-label={`Content of ${sel.type} v${sel.version}`}>
        {sel.content}
      </pre>
      {sorted.length >= 2 &&
        (sel.id === current.id ? (
          <p>Selected version is the current one; pick an older version to compare.</p>
        ) : (
          <>
            <h5>
              Changes from v{sel.version} to current v{current.version}
            </h5>
            <VersionDiff
              label={`Diff of ${sel.type} v${sel.version} against v${current.version}`}
              from={sel.content}
              to={current.content}
            />
          </>
        ))}
    </article>
  )
}
