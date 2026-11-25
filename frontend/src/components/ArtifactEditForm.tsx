import { useEffect, useRef, useState } from 'react'
import { ArtifactView, editArtifact } from '../api/artifacts'

/** Edit form for the CURRENT version: saves a new version; downstream stages are re-planned by the server. */
export default function ArtifactEditForm({
  runId,
  artifact,
  onSaved,
  onCancel,
}: {
  runId: string
  artifact: ArtifactView
  onSaved: () => void
  onCancel: () => void
}) {
  const [content, setContent] = useState(artifact.content)
  const [comment, setComment] = useState('')
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)
  const errRef = useRef<HTMLParagraphElement>(null)
  const base = `${artifact.nodeId}-${artifact.type}`

  useEffect(() => {
    if (err) errRef.current?.focus()
  }, [err])

  async function save(e: React.FormEvent) {
    e.preventDefault()
    if (!comment.trim()) {
      setErr('A comment is required')
      return
    }
    setBusy(true)
    setErr(null)
    try {
      await editArtifact(runId, { nodeId: artifact.nodeId, artifactType: artifact.type, content, comment: comment.trim() })
      onSaved()
    } catch (x) {
      setErr((x as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="artifact-edit" onSubmit={(e) => void save(e)} aria-label={`Edit ${artifact.type} v${artifact.version}`}>
      <p role="note">Downstream stages will be marked stale and re-run automatically</p>
      <label htmlFor={`edit-content-${base}`}>Edited content of {artifact.type}</label>
      <textarea id={`edit-content-${base}`} rows={12} value={content} onChange={(e) => setContent(e.target.value)} />
      <label htmlFor={`edit-comment-${base}`}>Comment (required)</label>
      <input id={`edit-comment-${base}`} type="text" aria-required="true" value={comment} onChange={(e) => setComment(e.target.value)} />
      {err && (
        <p role="alert" tabIndex={-1} ref={errRef} className="error">
          {err}
        </p>
      )}
      <button type="submit" disabled={busy}>
        Save
      </button>{' '}
      <button type="button" disabled={busy} onClick={onCancel}>
        Cancel
      </button>
    </form>
  )
}
