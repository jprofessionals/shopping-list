import { screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { render, createTestStore } from '../../test/testUtils';
import HouseholdList from './HouseholdList';

describe('HouseholdList', () => {
  it('renders empty state when no households', () => {
    render(<HouseholdList onCreateClick={() => {}} />);

    expect(screen.getByText(/no households yet/i)).toBeInTheDocument();
  });

  it('renders list of households', () => {
    const store = createTestStore({
      households: {
        items: [
          { id: '1', name: 'Home', createdAt: '2024-01-01', memberCount: 2, isOwner: true },
          { id: '2', name: 'Cabin', createdAt: '2024-01-02', memberCount: 1, isOwner: false },
        ],
        isLoading: false,
        error: null,
      },
      auth: { user: null, token: null, isAuthenticated: false, isLoading: false, error: null },
    });

    render(<HouseholdList onCreateClick={() => {}} />, { store });

    expect(screen.getByText('Home')).toBeInTheDocument();
    expect(screen.getByText('Cabin')).toBeInTheDocument();
  });

  it('shows create button', () => {
    render(<HouseholdList onCreateClick={() => {}} />);

    expect(screen.getByRole('button', { name: /create household/i })).toBeInTheDocument();
  });

  it('renders household items as links with correct href', () => {
    const store = createTestStore({
      households: {
        items: [
          {
            id: 'household-123',
            name: 'Test Household',
            createdAt: '2024-01-01',
            memberCount: 2,
            isOwner: true,
          },
        ],
        isLoading: false,
        error: null,
      },
      auth: { user: null, token: null, isAuthenticated: false, isLoading: false, error: null },
    });

    render(<HouseholdList onCreateClick={() => {}} />, { store });

    const link = screen.getByRole('link', { name: /test household/i });
    expect(link).toHaveAttribute('href', '/households/household-123');
  });
});
