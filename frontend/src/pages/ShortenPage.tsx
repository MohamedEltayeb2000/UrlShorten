import { FormEvent, useCallback, useEffect, useRef, useState } from 'react'
import { createLink, deactivateLink, listLinks, Link } from '../api/links'
import AnalyticsPanel from '../components/AnalyticsPanel'

export default function ShortenPage() {
  const [url, setUrl] = useState('')
  const [alias, setAlias] = useState('')
  const [expires, setExpires] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [created, setCreated] = useState<Link | null>(null)
  const [copied, setCopied] = useState(false)
  const [links, setLinks] = useState<Link[] | null>(null)
  const [listError, setListError] = useState<string | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const errorRef = useRef<HTMLParagraphElement>(null)

  const load = useCallback(async () => {
    try {
      setListError(null)
      setLinks((await listLinks()).items)
    } catch (e) {
      setListError((e as Error).message)
      setLinks([])
    }
    setRefreshKey((k) => k + 1)
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    if (error) errorRef.current?.focus()
  }, [error])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    setCreated(null)
    setCopied(false)
    try {
      const expiresAt = expires ? new Date(expires).toISOString() : undefined
      setCreated(await createLink(url, alias.trim(), expiresAt))
      setUrl('')
      setAlias('')
      setExpires('')
      void load()
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setSubmitting(false)
    }
  }

  async function copy() {
    if (!created) return
    try {
      await navigator.clipboard.writeText(created.shortUrl)
      setCopied(true)
    } catch {
      setCopied(false)
    }
  }

  async function deactivate(code: string) {
    try {
      await deactivateLink(code)
      void load()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  return (
    <section aria-labelledby="shorten-heading">
      <h2 id="shorten-heading">Shorten a link</h2>
      <form onSubmit={onSubmit}>
        <label htmlFor="url">Long URL</label>
        <input
          id="url"
          type="text"
          inputMode="url"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          placeholder="https://example.com/some/long/path"
          required
        />
        <label htmlFor="alias">Custom alias (optional)</label>
        <input id="alias" type="text" value={alias} onChange={(e) => setAlias(e.target.value)} maxLength={32} />
        <label htmlFor="expires">Expires at (optional)</label>
        <input id="expires" type="datetime-local" value={expires} onChange={(e) => setExpires(e.target.value)} />
        <button type="submit" disabled={submitting}>
          {submitting ? 'Shortening…' : 'Shorten'}
        </button>
      </form>

      {error && (
        <p role="alert" tabIndex={-1} ref={errorRef} className="error">
          {error}
        </p>
      )}

      {created && (
        <div role="status" className="result">
          <p>
            Your short link: <a href={created.shortUrl}>{created.shortUrl}</a>
          </p>
          <button type="button" onClick={copy}>
            {copied ? 'Copied' : 'Copy'}
          </button>
        </div>
      )}

      <h2>Your links</h2>
      {links === null && <p>Loading…</p>}
      {listError && <p role="alert" className="error">{listError}</p>}
      {links !== null && links.length === 0 && !listError && <p>No links yet. Create your first one above.</p>}
      {links !== null && links.length > 0 && (
        <table>
          <caption className="sr-only">Short links</caption>
          <thead>
            <tr>
              <th scope="col">Short link</th>
              <th scope="col">Original URL</th>
              <th scope="col">Status</th>
              <th scope="col">Clicks</th>
              <th scope="col">Expires</th>
              <th scope="col">Actions</th>
            </tr>
          </thead>
          <tbody>
            {links.map((l) => (
              <tr key={l.code}>
                <td>
                  <a href={l.shortUrl}>{l.code}</a>
                </td>
                <td className="url">{l.originalUrl}</td>
                <td>{l.status}</td>
                <td>{l.clicks}</td>
                <td>{l.expiresAt ? new Date(l.expiresAt).toLocaleString() : '—'}</td>
                <td>
                  {l.status === 'ACTIVE' && (
                    <button type="button" onClick={() => deactivate(l.code)} aria-label={`Deactivate ${l.code}`}>
                      Deactivate
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <AnalyticsPanel refreshKey={refreshKey} />
    </section>
  )
}
