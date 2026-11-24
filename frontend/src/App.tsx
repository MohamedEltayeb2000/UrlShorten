import { NavLink, Route, Routes } from 'react-router-dom'
import RunsPage from './pages/RunsPage'
import ShortenPage from './pages/ShortenPage'
import ArtifactsPage from './pages/ArtifactsPage'

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
        </nav>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<ShortenPage />} />
          <Route path="/runs" element={<RunsPage />} />
          <Route path="/runs/:id/artifacts" element={<ArtifactsPage />} />
          <Route path="*" element={<p>Page not found.</p>} />
        </Routes>
      </main>
    </>
  )
}
