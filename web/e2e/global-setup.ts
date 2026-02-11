import { execSync } from 'child_process';

async function globalSetup() {
  console.log('Starting E2E test environment via docker-compose...');

  try {
    // Start all services using the e2e docker-compose file
    execSync('docker compose -f docker-compose.e2e.yml up -d --build', {
      cwd: '..',
      stdio: 'inherit',
    });

    // Wait for backend to be ready
    console.log('Waiting for services to be ready...');
    let retries = 60; // 60 retries * 2 seconds = 2 minutes max wait
    while (retries > 0) {
      try {
        execSync('curl -sf http://localhost:8080/api/health', { stdio: 'pipe' });
        console.log('Backend is ready!');
        break;
      } catch {
        retries--;
        if (retries === 0) {
          // Show logs if services failed to start
          console.log('Services failed to become ready. Docker logs:');
          execSync('docker compose -f docker-compose.e2e.yml logs', {
            cwd: '..',
            stdio: 'inherit',
          });
          throw new Error('Services failed to become ready');
        }
        await new Promise((resolve) => setTimeout(resolve, 2000));
      }
    }

    // Wait for backend-2 to be ready
    retries = 60;
    while (retries > 0) {
      try {
        execSync('curl -sf http://localhost:8081/api/health', { stdio: 'pipe' });
        console.log('Backend-2 is ready!');
        break;
      } catch {
        retries--;
        if (retries === 0) {
          console.log('Backend-2 failed to become ready. Docker logs:');
          execSync('docker compose -f docker-compose.e2e.yml logs backend-2', {
            cwd: '..',
            stdio: 'inherit',
          });
          throw new Error('Backend-2 failed to become ready');
        }
        await new Promise((resolve) => setTimeout(resolve, 2000));
      }
    }

    // Wait for frontend to be ready
    retries = 30;
    while (retries > 0) {
      try {
        execSync('curl -sf http://localhost:5173', { stdio: 'pipe' });
        console.log('Frontend is ready!');
        break;
      } catch {
        retries--;
        if (retries === 0) {
          throw new Error('Frontend failed to become ready');
        }
        await new Promise((resolve) => setTimeout(resolve, 1000));
      }
    }

    console.log('All services are ready!');
  } catch (error) {
    console.error('Failed to start E2E environment:', error);
    throw error;
  }
}

export default globalSetup;
