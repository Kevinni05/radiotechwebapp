import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './web-tests',
  timeout: 120000,
  expect: { timeout: 15000 },
  fullyParallel: true,
  workers: process.env.CI ? 4 : undefined,
  use: { baseURL: 'http://127.0.0.1:18080', trace: 'retain-on-failure' },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'], launchOptions: { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined } } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    { name: 'webkit', workers: 1, use: { ...devices['Desktop Safari'] } },
  ],
  webServer: {
    command: 'java -jar build/libs/radiotech.jar --server.port=18080 --app.firebase.enabled=false --debug=false',
    url: 'http://127.0.0.1:18080/api/v1/health',
    reuseExistingServer: !process.env.CI,
    timeout: 60000,
  },
});
