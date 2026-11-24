import { FormEvent, useCallback, useEffect, useRef, useState } from 'react'
import { createRun, getRun, listRuns, pauseRun, startRun, RunDetail, RunSummary, Scenario } from '../api/runs'
import RunDetailPanel from '../components/RunDetailPanel'
import { usePolling } from '../hooks/usePolling'

const POLLED = ['RUNNING', 'WAITING_FOR_APPROVAL']

export default function RunsPage() {
  const [requirement, setRequirement] = useState('')
  const [scenario, setScenario] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [runs, setRuns] = useState<RunSummary[] | null>(null)
  const [selected, setSelected] = useState<RunDetail | null>(null)
  const errorRef = useRef<HTMLParagraphElement>(null)

  const load = useCallback(async () => {
    try {
      setRuns((await listRuns()).items)
    } catch (e) {
      setError((e as Error).message)
      setRuns([])
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    if (error) errorRef.current?.focus()
  }, [error])

  const selectedId = selected?.id
  const polling = usePolling(() => getRun(selectedId as number), !!selected && POLLED.includes(selected.status))

  useEffect(() => {
    const fresh = polling.data
    if (fresh) setSelected((cur) => (cur && cur.id === fresh.id ? fresh : cur))
  }, [polling.data])

  useEffect(() => {
    if (polling.error) setError(polling.error.message)
  }, [polling.error])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      setSelected(await createRun(requirement.trim(), (scenario || undefined) as Scenario | undefined))
      setRequirement('')
      void load()
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setSubmitting(false)
    }
  }

  async function select(id: number) {
    setError(null)
    try {
      setSelected(await getRun(id))
    } catch (err) {
      setError((err as Error).message)
    }
  }

  async function act(fn: (id: number) => Promise<RunDetail>) {
    if (!selected) return
    setBusy(true)
    setError(null)
    try {
      setSelected(await fn(selected.id))
      void load()
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section aria-labelledby="runs-heading">
      <h2 id="runs-heading">Agent runs</h2>
      <form onSubmit={onSubmit}>
        <label htmlFor="requirement">Requirement</label>
        <textarea
          id="requirement"
          rows={4}
          value={requirement}
          onChange={(e) => setRequirement(e.target.value)}
          required
        />
        <label htmlFor="scenario">Scenario</label>
        <select id="scenario" value={scenario} onChange={(e) => setScenario(e.target.value)}>
          <option value="">None</option>
          <option value="GREENFIELD">Greenfield</option>
          <option value="BROWNFIELD">Brownfield</option>
          <option value="AMBIGUOUS">Ambiguous</option>
        </select>
        <button type="submit" disabled={submitting}>
          {submitting ? 'Launching…' : 'Launch run'}
        </button>
      </form>

      {error && (
        <p role="alert" tabIndex={-1} ref={errorRef} className="error">
          {error}
        </p>
      )}

      <h3>Recent runs</h3>
      {runs === null && <p>Loading…</p>}
      {runs !== null && runs.length === 0 && <p>No runs yet</p>}
      {runs !== null && runs.length > 0 && (
        <ul className="run-list">
          {runs.map((r) => (
            <li key={r.id}>
              <button type="button" onClick={() => void select(r.id)} aria-label={`Open run ${r.id}`}>
                #{r.id}
              </button>{' '}
              {r.requirement} <span className={`status status-${r.status.toLowerCase()}`}>{r.status}</span>
            </li>
          ))}
        </ul>
      )}

      {selected && (
        <RunDetailPanel
          run={selected}
          busy={busy}
          onStart={() => void act(startRun)}
          onPause={() => void act(pauseRun)}
        />
      )}
    </section>
  )
}
