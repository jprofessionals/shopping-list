import { useState } from 'react';
import { Outlet, NavLink, useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAppSelector, useAppDispatch } from '../../store/hooks';
import { logout } from '../../store/authSlice';
import { ConnectionStatus } from '../common';
import BottomNav from './BottomNav';

export default function MainLayout() {
  const { t } = useTranslation();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { user } = useAppSelector((state) => state.auth);
  const [showCreateListModal, setShowCreateListModal] = useState(false);
  const [showCreateHouseholdModal, setShowCreateHouseholdModal] = useState(false);

  const handleLogout = () => {
    localStorage.removeItem('token');
    dispatch(logout());
    navigate('/');
  };

  // Handle add actions from BottomNav
  const handleAddList = () => {
    // Navigate to lists page with action param to trigger create modal
    if (window.location.pathname === '/lists') {
      // Already on lists page, trigger modal via state
      setShowCreateListModal(true);
    } else {
      navigate('/lists?action=create');
    }
  };

  const handleAddHousehold = () => {
    // Navigate to households page with action param to trigger create modal
    if (window.location.pathname === '/households') {
      // Already on households page, trigger modal via state
      setShowCreateHouseholdModal(true);
    } else {
      navigate('/households?action=create');
    }
  };

  // Check for action params on mount and clear them
  const actionParam = searchParams.get('action');
  if (actionParam === 'create') {
    const currentPath = window.location.pathname;
    if (currentPath === '/lists' && !showCreateListModal) {
      setShowCreateListModal(true);
      setSearchParams({});
    } else if (currentPath === '/households' && !showCreateHouseholdModal) {
      setShowCreateHouseholdModal(true);
      setSearchParams({});
    }
  }

  return (
    <div className="min-h-screen bg-gray-100 dark:bg-gray-900">
      {/* Header - fixed on desktop to work with sidebar */}
      <header className="fixed inset-x-0 top-0 z-50 bg-white shadow dark:bg-gray-800 dark:shadow-gray-900 md:left-20">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-4 sm:px-6 lg:px-8">
          <NavLink
            to="/"
            className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white md:text-3xl"
          >
            {t('app.shoppingList')}
          </NavLink>
          {user && (
            <div className="flex items-center gap-2 md:gap-4">
              <ConnectionStatus />
              <span className="hidden text-sm text-gray-600 dark:text-gray-300 sm:inline">
                {user.displayName}
              </span>
              <button
                onClick={handleLogout}
                className="rounded-md bg-gray-200 px-2 py-1 text-xs font-medium text-gray-700 hover:bg-gray-300 dark:bg-gray-700 dark:text-gray-200 dark:hover:bg-gray-600 md:px-3 md:py-1.5 md:text-sm"
              >
                {t('common.signOut')}
              </button>
            </div>
          )}
        </div>
      </header>

      {/* Main content area - adjusted for fixed header and bottom nav/sidebar */}
      <main className="pb-20 pt-16 md:pb-0 md:pl-20 md:pt-16">
        <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
          <Outlet
            context={{
              showCreateListModal,
              setShowCreateListModal,
              showCreateHouseholdModal,
              setShowCreateHouseholdModal,
            }}
          />
        </div>
      </main>

      {/* Bottom navigation (mobile) / Sidebar (desktop) */}
      <BottomNav onAddList={handleAddList} onAddHousehold={handleAddHousehold} />
    </div>
  );
}

// Export type for outlet context
export interface MainLayoutContext {
  showCreateListModal: boolean;
  setShowCreateListModal: (show: boolean) => void;
  showCreateHouseholdModal: boolean;
  setShowCreateHouseholdModal: (show: boolean) => void;
}
