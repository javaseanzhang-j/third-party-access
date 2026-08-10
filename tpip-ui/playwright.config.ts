import { defineConfig } from '@playwright/test'

const requestedChannel = process.env.TPIP_E2E_BROWSER_CHANNEL
const browserChannel = requestedChannel && requestedChannel !== 'bundled'
  ? requestedChannel as 'chrome' | 'msedge' : undefined

export default defineConfig({
  testDir: './e2e',
  outputDir: process.env.TPIP_E2E_OUTPUT_DIR || 'test-results',
  fullyParallel: false,
  retries: 0,
  use: { baseURL: 'http://127.0.0.1:18100', trace: 'retain-on-failure', channel: browserChannel },
  webServer: {
    command: 'CI=true npm run dev',
    url: 'http://127.0.0.1:18100',
    reuseExistingServer: true,
    timeout: 30_000
  }
})
