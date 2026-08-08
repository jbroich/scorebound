import { expect, test, type Page, type Route } from '@playwright/test'

const ADMIN_SESSION = {
  accountId: '10000000-0000-0000-0000-000000000001', username: 'admin', memberId: null,
  roles: ['Admin'], effectiveRoles: ['Admin'], mustChangePassword: false,
  preferredLocale: 'en', mode: 'Normal', csrfToken: 'test-csrf',
}
const DISPLAY_SESSION = {
  ...ADMIN_SESSION, accountId: '10000000-0000-0000-0000-000000000002', username: 'wall',
  roles: ['Display'], effectiveRoles: ['Display'], mode: 'Display',
}
const BOARD_ID = '20000000-0000-0000-0000-000000000001'
const PERIOD_ID = '30000000-0000-0000-0000-000000000001'
const RED_ID = '40000000-0000-0000-0000-000000000001'
const BLUE_ID = '40000000-0000-0000-0000-000000000002'
const GREEN_ID = '40000000-0000-0000-0000-000000000003'

async function fulfillJson(route: Route, body: unknown, status = 200) {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

async function installEventSource(page: Page) {
  await page.addInitScript(() => {
    class TestEventSource {
      static instance: TestEventSource | null = null
      private listeners = new Map<string, Array<(event: Event) => void>>()
      constructor(_url: string | URL, _init?: EventSourceInit) {
        TestEventSource.instance = this
        Object.assign(window, { __scoreboundEventSource: this })
        setTimeout(() => this.emit('snapshot'), 0)
      }
      addEventListener(type: string, listener: EventListenerOrEventListenerObject | null) {
        if (!listener) return
        const callback = typeof listener === 'function' ? listener : listener.handleEvent.bind(listener)
        this.listeners.set(type, [...(this.listeners.get(type) ?? []), callback])
      }
      emit(type: string) {
        this.listeners.get(type)?.forEach((listener) => listener(new Event(type)))
      }
      close() {}
    }
    Object.assign(window, { EventSource: TestEventSource })
  })
}

function standings(redScore: number) {
  return {
    periodId: PERIOD_ID, periodName: 'Training 2026', status: 'Active',
    startsAt: '2026-08-01T08:00:00Z', endsAt: '2026-08-31T16:00:00Z', visualCeiling: 100,
    standings: [
      { teamId: RED_ID, teamName: 'Red Comets', shortName: 'RED', color: '#E5484D', score: redScore, rank: 1, winner: false },
      { teamId: BLUE_ID, teamName: 'Blue Orbit', shortName: 'BLU', color: '#3366FF', score: 40, rank: 2, winner: false },
      { teamId: GREEN_ID, teamName: 'Green Sparks', shortName: 'GRN', color: '#30A46C', score: 20, rank: 3, winner: false },
    ],
  }
}

test('admin scores on a smartphone by keyboard and sees cancelled history', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await installEventSource(page)
  let redScore = 0
  let submittedBody: Record<string, unknown> | null = null
  const cancelled = {
    id: '50000000-0000-0000-0000-000000000001', periodId: PERIOD_ID, teamId: RED_ID,
    teamName: 'Red Comets', memberId: null, memberName: null, kind: 'Credit', amount: 25,
    reason: 'Duplicate entry', createdAt: '2026-08-07T10:00:00Z', createdBy: ADMIN_SESSION.accountId,
    createdByUsername: 'admin', resultingTeamScore: null,
    cancellation: { reason: 'Entered twice', createdAt: '2026-08-07T10:01:00Z', createdBy: ADMIN_SESSION.accountId, createdByUsername: 'admin' },
  }

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request(); const path = new URL(request.url()).pathname
    if (path === '/api/v1/session') return fulfillJson(route, ADMIN_SESSION)
    if (path === '/api/v1/scoreboards') return fulfillJson(route, [{ id: BOARD_ID, name: 'Apprentice League', description: null, active: true }])
    if (path === '/api/v1/members') return fulfillJson(route, { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 })
    if (path.endsWith('/standings')) return fulfillJson(route, standings(redScore))
    if (path.endsWith('/transactions') && request.method() === 'GET') return fulfillJson(route, { periodId: PERIOD_ID, content: [cancelled], page: 0, size: 50, totalElements: 1, totalPages: 1 })
    if (path.endsWith('/transactions') && request.method() === 'POST') {
      const body = request.postDataJSON() as Record<string, unknown>
      submittedBody = body
      redScore += Number(body.amount)
      return fulfillJson(route, { ...cancelled, id: '50000000-0000-0000-0000-000000000002', amount: body.amount, reason: body.reason, cancellation: null, resultingTeamScore: redScore })
    }
    return fulfillJson(route, { status: 404, code: 'not_found' }, 404)
  })

  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Score', exact: true })).toBeVisible()
  await expect(page.locator('.mobile-nav')).toBeVisible()
  await expect(page.locator('.transaction--cancelled')).toContainText('Entered twice')
  await expect(page.locator('.transaction--cancelled .transaction__amount')).toHaveCSS('text-decoration-line', 'line-through')

  await page.getByRole('button', { name: '50' }).click()
  await page.getByLabel('Reason').focus()
  await page.keyboard.type('Excellent presentation')
  const submit = page.getByRole('button', { name: /50.*Book score/ })
  await submit.focus()
  await expect(submit).toBeFocused()
  await page.keyboard.press('Enter')

  await expect.poll(() => submittedBody).not.toBeNull()
  expect(submittedBody).toMatchObject({ teamId: RED_ID, kind: 'Credit', amount: 50, reason: 'Excellent presentation' })
  await expect(page.locator('.standing').filter({ hasText: 'Red Comets' }).locator('b')).toHaveText('50')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})

test('landscape display reconciles live scores and disables motion when requested', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await installEventSource(page)
  let redScore = 80

  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/session') return fulfillJson(route, DISPLAY_SESSION)
    if (path === '/api/v1/display/configuration') return fulfillJson(route, { mode: 'Fixed', fixedScoreboardId: BOARD_ID, rotationSeconds: 25, soundEnabled: false, scoreboards: [{ id: BOARD_ID, name: 'Apprentice League', description: null }] })
    if (path.endsWith('/standings')) return fulfillJson(route, standings(redScore))
    if (path.endsWith('/transactions')) return fulfillJson(route, { periodId: PERIOD_ID, content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 })
    return fulfillJson(route, { status: 404, code: 'not_found' }, 404)
  })

  await page.goto('/display')
  await expect(page.getByRole('heading', { name: 'Apprentice League' })).toBeVisible()
  await expect(page.locator('.glass-team')).toHaveCount(3)
  await expect(page.locator('.glass-team').first()).toContainText('#1')
  await expect(page.locator('.glass-team').first()).toContainText('80')
  expect(await page.locator('.glass-team').first().locator('.ball-field i').count()).toBeGreaterThan(await page.locator('.glass-team').last().locator('.ball-field i').count())
  await expect(page.locator('.ball-field i').first()).toHaveCSS('animation-name', 'none')
  const transitionSeconds = await page.locator('.ball-field').first().evaluate((element) => Number.parseFloat(getComputedStyle(element).transitionDuration))
  expect(transitionSeconds).toBeLessThanOrEqual(0.001)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

  redScore = 90
  await page.evaluate(() => {
    const source = (window as unknown as { __scoreboundEventSource: { emit: (type: string) => void } }).__scoreboundEventSource
    source.emit('score-created')
  })
  await expect(page.locator('.glass-team').first()).toContainText('90')
})

test('unauthenticated users cannot reach protected management controls', async ({ page }) => {
  await page.route('**/api/v1/session', (route) => fulfillJson(route, { status: 401, code: 'authentication_required' }, 401))
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Score' })).toHaveCount(0)
})
