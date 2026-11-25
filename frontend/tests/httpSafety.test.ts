import { describe, it, expect } from 'vitest'
import { seg, int, http } from '../src/api/http'

describe('request path safety', () => {
  it('accepts plain identifiers and rejects traversal or separators', () => {
    expect(seg('abc_12-X')).toBe('abc_12-X')
    expect(seg(42)).toBe('42')
    for (const bad of ['../x', 'a/b', 'a?b', '%2e%2e', '', 'http://evil']) expect(() => seg(bad)).toThrow()
  })
  it('accepts only small non-negative integers', () => {
    expect(int(3)).toBe(3)
    for (const bad of [-1, 1.5, NaN, 1e9]) expect(() => int(bad)).toThrow()
  })
  it('refuses paths that leave the same-origin /api tree', async () => {
    for (const bad of ['https://evil.example/api/x', '//evil/api/x', '/api/../admin', '/other/x']) await expect(http(bad)).rejects.toThrow()
  })
})
