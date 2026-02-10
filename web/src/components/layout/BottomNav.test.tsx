import { screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { render, createTestStore } from '../../test/testUtils';
import BottomNav from './BottomNav';

const defaultAuthState = {
  auth: {
    user: { id: '1', email: 'test@example.com', displayName: 'Test User', avatarUrl: null },
    token: 'test-token',
    isAuthenticated: true,
    isLoading: false,
    error: null,
  },
};

describe('BottomNav', () => {
  describe('Navigation Items', () => {
    it('renders all five navigation items', () => {
      const store = createTestStore(defaultAuthState);
      render(<BottomNav />, { store });

      // There are two navs (mobile and desktop) so use getAllBy
      expect(screen.getAllByRole('link', { name: /home/i }).length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByRole('link', { name: /lists/i }).length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByRole('link', { name: /households/i }).length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByRole('link', { name: /profile/i }).length).toBeGreaterThanOrEqual(1);
      expect(
        screen.getAllByRole('button', { name: /add new item/i }).length
      ).toBeGreaterThanOrEqual(1);
    });

    it('renders correct hrefs for navigation links', () => {
      const store = createTestStore(defaultAuthState);
      render(<BottomNav />, { store });

      // Get first of each link type (there are duplicates for mobile/desktop)
      expect(screen.getAllByRole('link', { name: /home/i })[0]).toHaveAttribute('href', '/');
      expect(screen.getAllByRole('link', { name: /lists/i })[0]).toHaveAttribute('href', '/lists');
      expect(screen.getAllByRole('link', { name: /households/i })[0]).toHaveAttribute(
        'href',
        '/households'
      );
      expect(screen.getAllByRole('link', { name: /profile/i })[0]).toHaveAttribute(
        'href',
        '/settings'
      );
    });

    it('has dual navigation areas (mobile bottom and desktop sidebar)', () => {
      const store = createTestStore(defaultAuthState);
      render(<BottomNav />, { store });

      // Should have two nav elements for mobile and desktop
      const navElements = screen.getAllByRole('navigation', { name: /main navigation/i });
      expect(navElements).toHaveLength(2);
    });
  });

  describe('Active State Highlighting', () => {
    it('highlights home link when on home route', () => {
      const store = createTestStore(defaultAuthState);
      render(<BottomNav />, {
        store,
        routerProps: { initialEntries: ['/'] },
      });

      const homeLinks = screen.getAllByRole('link', { name: /home/i });
      // Check at least one has active state (indigo color)
      const hasActiveLink = homeLinks.some((link) => link.className.includes('text-indigo-600'));
      expect(hasActiveLink).toBe(true);
    });

    it('highlights lists link when on lists route', () => {
      const store = createTestStore(defaultAuthState);
      render(<BottomNav />, {
        store,
        routerProps: { initialEntries: ['/lists'] },
      });

      const listsLinks = screen.getAllByRole('link', { name: /lists/i });
      const hasActiveLink = listsLinks.some((link) => link.className.includes('text-indigo-600'));
      expect(hasActiveLink).toBe(true);
    });

    it('highlights households link when on households route', () => {
      const store = createTestStore(defaultAuthState);
      render(<BottomNav />, {
        store,
        routerProps: { initialEntries: ['/households'] },
      });

      const householdsLinks = screen.getAllByRole('link', { name: /households/i });
      const hasActiveLink = householdsLinks.some((link) =>
        link.className.includes('text-indigo-600')
      );
      expect(hasActiveLink).toBe(true);
    });

    it('highlights profile link when on settings route', () => {
      const store = createTestStore(defaultAuthState);
      render(<BottomNav />, {
        store,
        routerProps: { initialEntries: ['/settings'] },
      });

      const profileLinks = screen.getAllByRole('link', { name: /profile/i });
      const hasActiveLink = profileLinks.some((link) => link.className.includes('text-indigo-600'));
      expect(hasActiveLink).toBe(true);
    });

    it('does not highlight home when on different route', () => {
      const store = createTestStore(defaultAuthState);
      render(<BottomNav />, {
        store,
        routerProps: { initialEntries: ['/lists'] },
      });

      const homeLinks = screen.getAllByRole('link', { name: /home/i });
      const hasActiveLink = homeLinks.some((link) => link.className.includes('text-indigo-600'));
      expect(hasActiveLink).toBe(false);
    });
  });

  describe('Add Button', () => {
    it('renders add button in center position', () => {
      const store = createTestStore(defaultAuthState);
      render(<BottomNav />, { store });

      // Should have add buttons (one for mobile, one for desktop)
      const addButtons = screen.getAllByRole('button', { name: /add new item/i });
      expect(addButtons.length).toBeGreaterThanOrEqual(1);
    });

    it('opens dropdown menu when add button is clicked', async () => {
      const store = createTestStore(defaultAuthState);
      render(<BottomNav />, { store });

      const addButtons = screen.getAllByRole('button', { name: /add new item/i });
      fireEvent.click(addButtons[0]);

      await waitFor(() => {
        expect(screen.getByRole('menuitem', { name: /new shopping list/i })).toBeInTheDocument();
      });
    });

    it('shows both create options when not on lists or households page', async () => {
      const store = createTestStore(defaultAuthState);
      render(<BottomNav />, {
        store,
        routerProps: { initialEntries: ['/'] },
      });

      const addButtons = screen.getAllByRole('button', { name: /add new item/i });
      fireEvent.click(addButtons[0]);

      await waitFor(() => {
        expect(screen.getByRole('menuitem', { name: /new shopping list/i })).toBeInTheDocument();
        expect(screen.getByRole('menuitem', { name: /new household/i })).toBeInTheDocument();
      });
    });

    it('calls onAddList when new shopping list is clicked', async () => {
      const onAddList = vi.fn();
      const store = createTestStore(defaultAuthState);
      render(<BottomNav onAddList={onAddList} />, { store });

      const addButtons = screen.getAllByRole('button', { name: /add new item/i });
      fireEvent.click(addButtons[0]);

      await waitFor(() => {
        expect(screen.getByRole('menuitem', { name: /new shopping list/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('menuitem', { name: /new shopping list/i }));

      expect(onAddList).toHaveBeenCalled();
    });

    it('calls onAddHousehold when new household is clicked', async () => {
      const onAddHousehold = vi.fn();
      const store = createTestStore(defaultAuthState);
      render(<BottomNav onAddHousehold={onAddHousehold} />, { store });

      const addButtons = screen.getAllByRole('button', { name: /add new item/i });
      fireEvent.click(addButtons[0]);

      await waitFor(() => {
        expect(screen.getByRole('menuitem', { name: /new household/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('menuitem', { name: /new household/i }));

      expect(onAddHousehold).toHaveBeenCalled();
    });

    it('closes dropdown when menu item is clicked', async () => {
      const onAddList = vi.fn();
      const store = createTestStore(defaultAuthState);
      render(<BottomNav onAddList={onAddList} />, { store });

      const addButtons = screen.getAllByRole('button', { name: /add new item/i });
      fireEvent.click(addButtons[0]);

      await waitFor(() => {
        expect(screen.getByRole('menuitem', { name: /new shopping list/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('menuitem', { name: /new shopping list/i }));

      await waitFor(() => {
        expect(
          screen.queryByRole('menuitem', { name: /new shopping list/i })
        ).not.toBeInTheDocument();
      });
    });

    it('closes dropdown when clicking outside', async () => {
      const store = createTestStore(defaultAuthState);
      render(
        <div>
          <div data-testid="outside">Outside</div>
          <BottomNav />
        </div>,
        { store }
      );

      const addButtons = screen.getAllByRole('button', { name: /add new item/i });
      fireEvent.click(addButtons[0]);

      await waitFor(() => {
        expect(screen.getByRole('menuitem', { name: /new shopping list/i })).toBeInTheDocument();
      });

      fireEvent.mouseDown(screen.getByTestId('outside'));

      await waitFor(() => {
        expect(
          screen.queryByRole('menuitem', { name: /new shopping list/i })
        ).not.toBeInTheDocument();
      });
    });

    it('closes dropdown when escape key is pressed', async () => {
      const store = createTestStore(defaultAuthState);
      render(<BottomNav />, { store });

      const addButtons = screen.getAllByRole('button', { name: /add new item/i });
      fireEvent.click(addButtons[0]);

      await waitFor(() => {
        expect(screen.getByRole('menuitem', { name: /new shopping list/i })).toBeInTheDocument();
      });

      fireEvent.keyDown(document, { key: 'Escape' });

      await waitFor(() => {
        expect(
          screen.queryByRole('menuitem', { name: /new shopping list/i })
        ).not.toBeInTheDocument();
      });
    });

    it('has proper aria attributes on add button', () => {
      const store = createTestStore(defaultAuthState);
      render(<BottomNav />, { store });

      const addButtons = screen.getAllByRole('button', { name: /add new item/i });
      expect(addButtons[0]).toHaveAttribute('aria-haspopup', 'true');
    });
  });

  describe('Accessibility', () => {
    it('has navigation landmark roles', () => {
      const store = createTestStore(defaultAuthState);
      render(<BottomNav />, { store });

      const navElements = screen.getAllByRole('navigation');
      expect(navElements.length).toBeGreaterThanOrEqual(1);
    });

    it('navigation has accessible name', () => {
      const store = createTestStore(defaultAuthState);
      render(<BottomNav />, { store });

      expect(screen.getAllByRole('navigation', { name: /main navigation/i })).toHaveLength(2);
    });

    it('dropdown menu has proper role', async () => {
      const store = createTestStore(defaultAuthState);
      render(<BottomNav />, { store });

      const addButtons = screen.getAllByRole('button', { name: /add new item/i });
      fireEvent.click(addButtons[0]);

      await waitFor(() => {
        expect(screen.getByRole('menu')).toBeInTheDocument();
      });
    });
  });
});
