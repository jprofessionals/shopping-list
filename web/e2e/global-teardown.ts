import { execSync } from 'child_process';

async function globalTeardown() {
  // Stop all E2E services
  console.log('Stopping E2E test environment...');
  try {
    execSync('docker compose -f docker-compose.e2e.yml down', {
      cwd: '..',
      stdio: 'inherit',
    });
  } catch (error) {
    console.error('Failed to stop E2E environment:', error);
  }
}

export default globalTeardown;
