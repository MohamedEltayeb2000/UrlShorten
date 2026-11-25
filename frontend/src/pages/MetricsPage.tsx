import OverallMetricsSection from '../components/OverallMetricsSection'
import RunMetricsSection from '../components/RunMetricsSection'
import ScenarioCards from '../components/ScenarioCards'

export default function MetricsPage() {
  return (
    <section aria-labelledby="metrics-heading">
      <h2 id="metrics-heading">Metrics</h2>
      <OverallMetricsSection />
      <ScenarioCards />
      <RunMetricsSection />
    </section>
  )
}
