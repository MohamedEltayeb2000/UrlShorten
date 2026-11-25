// @req 001-FR-037
// @req 001-FR-023
// @req 001-FR-027
import { expect, test } from '@playwright/test'

test('shorten a link, follow it, see the click and deactivate it', async ({ page, request }) => {
  const alias = `e2e-${Date.now()}`
  await page.goto('/')
  await page.getByLabel('Long URL').fill('https://example.com/end-to-end')
  await page.getByLabel('Custom alias (optional)').fill(alias)
  await page.getByRole('button', { name: 'Shorten' }).click()
  await expect(page.getByRole('status')).toContainText(`/${alias}`)

  // the real redirect (no auto-follow) and the click shows up in the list after a reload
  const res = await request.get(`http://localhost:8080/${alias}`, { maxRedirects: 0 })
  expect(res.status()).toBe(302)
  expect(res.headers()['location']).toBe('https://example.com/end-to-end')
  await page.reload()
  const row = page.getByRole('row', { name: new RegExp(alias) })
  await expect(row).toContainText('1')

  await row.getByRole('button', { name: `Deactivate ${alias}` }).click()
  await expect(page.getByRole('row', { name: new RegExp(alias) })).toContainText('INACTIVE')
  const gone = await request.get(`http://localhost:8080/${alias}`, { maxRedirects: 0 })
  expect(gone.status()).toBe(404)
})

test('an unsafe URL is rejected with a clear message', async ({ page }) => {
  await page.goto('/')
  await page.getByLabel('Long URL').fill('javascript:alert(1)')
  await page.getByRole('button', { name: 'Shorten' }).click()
  const alert = page.getByRole('alert').first()
  await expect(alert).toContainText(/http and https/i)
  await expect(alert).toBeFocused()
})
