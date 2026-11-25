import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ScenarioPreset, launchScenario, listScenarios } from '../api/metrics'
import { ErrorAlert, useLoaded } from './metricsUi'

export default function ScenarioCards() {
  const navigate = useNavigate()
  const { data, error } = useLoaded(listScenarios, 'scenarios')
  const [launching, setLaunching] = useState<string | null>(null)
  const [launchError, setLaunchError] = useState<string | null>(null)

  async function launch(s: ScenarioPreset) {
    setLaunching(s.id)
    setLaunchError(null)
    try {
      await launchScenario(s.id)
      navigate('/runs')
    } catch (e) {
      setLaunchError((e as Error).message)
      setLaunching(null)
    }
  }

  return (
    <section aria-labelledby="scenarios-heading">
      <h3 id="scenarios-heading">Scenarios</h3>
      {error && <ErrorAlert message={error} />}
      {launchError && <ErrorAlert message={launchError} />}
      {!error && !data && <p>Loading scenarios…</p>}
      {data && data.length === 0 && <p>No scenarios available</p>}
      {data && data.length > 0 && (
        <ul className="scenario-list" aria-label="Scenario presets">
          {data.map((s) => (
            <li key={s.id}>
              <article className="scenario-card" aria-labelledby={`scenario-${s.id}`}>
                <h4 id={`scenario-${s.id}`}>{s.name}</h4>
                <p>{s.description}</p>
                <p className="note">{s.requirement}</p>
                <button
                  type="button"
                  onClick={() => void launch(s)}
                  disabled={launching !== null}
                  aria-label={`Launch ${s.name}`}
                >
                  {launching === s.id ? 'Launching…' : 'Launch'}
                </button>
              </article>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
