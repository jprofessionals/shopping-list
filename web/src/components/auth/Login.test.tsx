import { describe, it, expect } from 'vitest';
import { render, screen } from '../../test/testUtils';
import Login from './Login';

describe('Login', () => {
  it('renders login form when local auth is enabled', () => {
    render(
      <Login authConfig={{ googleEnabled: false, localEnabled: true, googleClientId: null }} />
    );

    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });

  it('renders Google login button when Google auth is enabled', () => {
    render(
      <Login
        authConfig={{ googleEnabled: true, localEnabled: false, googleClientId: 'test-client' }}
      />
    );

    expect(screen.getByRole('button', { name: /google/i })).toBeInTheDocument();
  });

  it('renders both options when both are enabled', () => {
    render(
      <Login
        authConfig={{ googleEnabled: true, localEnabled: true, googleClientId: 'test-client' }}
      />
    );

    expect(screen.getByRole('button', { name: /google/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });
});
