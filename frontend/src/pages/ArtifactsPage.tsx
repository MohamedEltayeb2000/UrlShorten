import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { ArtifactView, listArtifacts } from '../api/artifacts'
import ArtifactViewer from '../components/ArtifactViewer'
import AuditTimeline from '../components/AuditTimeline'

function groupBy<T>(items: T[], key: (t: T) => string): [string, T[]][] {
  const m = new Map<string, T[]>()
  for (const i of items) m.set(key(i), [...(m.get(key(i)) ?? []), i])
  return [...m.entries()]
}

export default function ArtifactsPage() {
  const { id = '' } = useParams()
  const [artifacts, setArtifacts] = useState<ArtifactView[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const errRef = useRef<HTMLParagraphElement>(null)

  const [reload, setReload] = useState(0)

  useEffect(() => {
    let cancelled = false
    setError(null)
    listArtifacts(id)
      .then((a) => !cancelled && setArtifacts(a))
      .catch((e: Error) => !cancelled && setError(e.message))
    return () => {
      cancelled = true
    }
  }, [id, reload])

  useEffect(() => setArtifacts(null), [id])

  useEffect(() => {
    if (error) errRef.current?.focus()
  }, [error])

  return (
    <section aria-labelledby="artifacts-heading">
      <h2 id="artifacts-heading">Artifacts for run {id}</h2>
      {error && (
        <p role="alert" tabIndex={-1} ref={errRef} className="error">
          {error}
        </p>
      )}
      {!error && artifacts === null && <p>Loading artifacts…</p>}
      {artifacts && artifacts.length === 0 && <p>No artifacts yet</p>}
      {artifacts &&
        groupBy(artifacts, (a) => a.nodeId).map(([stage, items]) => (
          <section key={stage} aria-labelledby={`stage-${stage}`} className="panel">
            <h3 id={`stage-${stage}`}>Stage: {stage}</h3>
            {groupBy(items, (a) => a.type).map(([type, versions]) => (
              <ArtifactViewer
                key={type + versions.map((v) => v.id + v.status).join()}
                versions={versions}
                runId={id}
                onChanged={() => setReload((n) => n + 1)}
              />
            ))}
          </section>
        ))}
      <AuditTimeline runId={id} />
    </section>
  )
}
