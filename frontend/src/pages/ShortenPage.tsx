import { FormEvent, useCallback, useEffect, useRef, useState } from 'react'
import { createLink, listLinks, Link } from '../api/links'

export default function ShortenPage() {
  const [url, setUrl] = useState('')
  const [alias, setAlias] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [created, setCreated] = useState<Link | null>(null)
  const [copied, setCopied] = useState(false)
  const [links, setLinks] = useState<Link[] | null>(null)
  const [listError, setListError] = useState<string | null>(null)
  const errorRef = useRef<HTMLParagraphElement>(null)

  const load = useCallback(async () => {
    try {
      setListError(null)
      setLinks((await listLinks()).items)
    } catch (e) {
      setListError((e as Error).message)
      setLinks([])
    }
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
      setCreated(await createLink(url, alias.trim()))
      setUrl('')
      setAlias('')
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
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
