import { defineConfig } from '@playwright/test'

// Real-browser journeys against the real backend (embedded H2, in-memory, stages without artificial delay) and the Vite dev server.
export default defineConfig({
  testDir: './e2e',
  timeout: 90_000,
  workers: 1,
  fullyParallel: false,
  reporter: [['list'], ['json', { outputFile: 'e2e/results.json' }]],
  use: { baseURL: 'http://localhost:5173', trace: 'retain-on-failure' },
  webServer: [
    {
      command:
        'mvn -q -B -f ../backend/pom.xml spring-boot:run "-Dspring-boot.run.profiles=local" ' +
        '"-Dspring-boot.run.arguments=--spring.datasource.url=jdbc:h2:mem:e2e;MODE=PostgreSQL;DB_CLOSE_DELAY=-1 ' +
        '--shortener.orchestrator.stage-delay-ms=0"',
      url: 'http://localhost:8080/actuator/health',
      timeout: 180_000,
      reuseExistingServer: !process.env.CI,
    },
    {
      command: 'npm run dev -- --port 5173 --strictPort',
      url: 'http://localhost:5173',
      timeout: 60_000,
      reuseExistingServer: !process.env.CI,
    },
  ],
})
