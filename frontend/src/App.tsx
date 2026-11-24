import { NavLink, Route, Routes } from 'react-router-dom'
import ShortenPage from './pages/ShortenPage'

export default function App() {
  return (
    <>
      <header className="app-header">
        <h1>URL Shortener</h1>
        <nav aria-label="Main">
          <NavLink to="/" end>
            Shorten
          </NavLink>
        </nav>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<ShortenPage />} />
          <Route path="*" element={<p>Page not found.</p>} />
        </Routes>
      </main>
    </>
  )
}
