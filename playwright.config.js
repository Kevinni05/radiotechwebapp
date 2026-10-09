import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './web-tests',
  timeout: 30000,
  fullyParallel: true,
  workers: process.env.CI ? 4 : undefined,
  use: { baseURL: 'http://127.0.0.1:18080', browserName: 'chromium', trace: 'retain-on-failure' },
  webServer: {
    command: 'java -jar build/libs/radiotech.jar --server.port=18080 --app.firebase.enabled=false --debug=false',
    url: 'http://127.0.0.1:18080/api/v1/health',
    reuseExistingServer: !process.env.CI,
    timeout: 60000,
  },
});
