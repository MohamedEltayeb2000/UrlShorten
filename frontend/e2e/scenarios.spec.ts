// @req 001-FR-037
// @req 001-FR-018
// @req 001-FR-011
// @req 001-FR-019
// @req 001-FR-020
import { expect, test, Page } from '@playwright/test'

async function launch(page: Page, name: string) {
  await page.goto('/metrics')
  await page.getByRole('button', { name: `Launch ${name}` }).click()
  await expect(page).toHaveURL(/\/runs/)
  await page.getByRole('button', { name: /Open run \d+/ }).first().click()
  await page.getByRole('button', { name: 'Start' }).click()
}

async function approveFirst(page: Page, comment: string) {
  await page.goto('/approvals')
  const card = page.getByRole('article').first()
  await expect(card).toBeVisible({ timeout: 30_000 })
  const ticket = card.getByLabel('Change ticket')
  if (await ticket.count()) {
    await ticket.fill('CHG-1042')
    await card.getByLabel('Risk justification').fill('Reviewed the proposal and the test report')
  }
  await card.getByLabel(/Comment/).fill(comment)
  await card.getByRole('button', { name: 'Approve' }).click()
  await expect(card).toContainText(/APPROVED/i)
}

test('Greenfield: both approval gates, then metrics and the summary', async ({ page }) => {
  await launch(page, 'Greenfield')
  await expect(page.getByText('WAITING_FOR_APPROVAL').first()).toBeVisible({ timeout: 30_000 })
  await approveFirst(page, 'patch reviewed')
  await page.goto('/runs')
  await page.getByRole('button', { name: /Open run \d+/ }).first().click()
  await expect(page.getByText('WAITING_FOR_APPROVAL').first()).toBeVisible({ timeout: 30_000 })
  await approveFirst(page, 'release approved')
  await page.goto('/runs')
  await page.getByRole('button', { name: /Open run \d+/ }).first().click()
  await expect(page.getByText(/Status:\s*SUCCEEDED/)).toBeVisible({ timeout: 30_000 })
  await expect(page.getByText('Entry gate: passed').first()).toBeVisible()

  await page.getByRole('link', { name: /View artifacts/ }).click()
  await expect(page.getByText('Proposal - not applied')).toBeVisible()

  await page.goto('/metrics')
  await expect(page.getByText(/success rate/i).first()).toBeVisible()
})

test('Ambiguous: stops with questions and resumes only after a human decision', async ({ page }) => {
  await launch(page, 'Ambiguous requirement')
  await expect(page.getByText('WAITING_FOR_APPROVAL').first()).toBeVisible({ timeout: 30_000 })
  await page.goto('/approvals')
  const card = page.getByRole('article').first()
  await expect(card).toContainText(/CLARIFICATION/i, { timeout: 30_000 })
  await expect(card).toContainText(/\?/) // the open questions are shown to the reviewer
  await card.getByLabel(/Comment/).fill('proceed with the stated safe assumptions')
  await card.getByRole('button', { name: 'Approve' }).click()
  await expect(card).toContainText(/APPROVED/i)
})
