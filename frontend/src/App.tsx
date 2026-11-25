import { NavLink, Route, Routes } from 'react-router-dom'
import RunsPage from './pages/RunsPage'
import ShortenPage from './pages/ShortenPage'
import ArtifactsPage from './pages/ArtifactsPage'
import ApprovalsPage from './pages/ApprovalsPage'
import MetricsPage from './pages/MetricsPage'

export default function App() {
  return (
    <>
      <header className="app-header">
        <h1>URL Shortener</h1>
        <nav aria-label="Main">
          <NavLink to="/" end>
            Shorten
          </NavLink>
          <NavLink to="/runs">Runs</NavLink>
          <NavLink to="/approvals">Approvals</NavLink>
          <NavLink to="/metrics">Metrics</NavLink>
        </nav>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<ShortenPage />} />
          <Route path="/runs" element={<RunsPage />} />
          <Route path="/approvals" element={<ApprovalsPage />} />
          <Route path="/metrics" element={<MetricsPage />} />
          <Route path="/runs/:id/artifacts" element={<ArtifactsPage />} />
          <Route path="*" element={<p>Page not found.</p>} />
        </Routes>
      </main>
    </>
  )
}
