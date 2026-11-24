// @req 002-FR-016
// @req 002-FR-018
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

const ALLOWED_DEPENDENCIES = ['react', 'react-dom', 'react-router-dom']
const MAX_PAGE_LINES = 250

describe('frontend simplicity', () => {
  it('uses only the minimal allow-listed runtime dependencies (no UI or state libraries)', () => {
    const pkg = JSON.parse(readFileSync('package.json', 'utf8'))
    expect(Object.keys(pkg.dependencies).sort()).toEqual([...ALLOWED_DEPENDENCIES].sort())
  })

  it('keeps every page small and readable', () => {
    const dir = join('src', 'pages')
    for (const f of readdirSync(dir).filter((n: string) => statSync(join(dir, n)).isFile())) {
      const lines = readFileSync(join(dir, f), 'utf8').split('\n').length
      expect(lines, `${f} has ${lines} lines`).toBeLessThanOrEqual(MAX_PAGE_LINES)
    }
  })

  it('provides the pages planned for the current phase', () => {
    expect(statSync(join('src', 'pages', 'ShortenPage.tsx')).isFile()).toBe(true)
  })
})
