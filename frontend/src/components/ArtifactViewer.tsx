import { useState } from 'react'
import type { ArtifactView } from '../api/artifacts'
import VersionDiff from './VersionDiff'

/** One (stage, type) group: version selector, provenance, content, proposal banner and diff vs current. */
export default function ArtifactViewer({ versions }: { versions: ArtifactView[] }) {
  const sorted = [...versions].sort((a, b) => a.version - b.version)
  const current = sorted.find((v) => v.status === 'CURRENT') ?? sorted[sorted.length - 1]
  const [selectedId, setSelectedId] = useState(current.id)
  const sel = sorted.find((v) => v.id === selectedId) ?? current
  const base = `${sel.nodeId}-${sel.type}`
  const selectId = `ver-${base}`

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
