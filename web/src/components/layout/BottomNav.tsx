import { useState, useRef, useEffect } from 'react';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

// Icon components using Heroicons-style SVGs
function HomeIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      fill="none"
      viewBox="0 0 24 24"
      strokeWidth={1.5}
      stroke="currentColor"
    >
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="m2.25 12 8.954-8.955c.44-.439 1.152-.439 1.591 0L21.75 12M4.5 9.75v10.125c0 .621.504 1.125 1.125 1.125H9.75v-4.875c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21h4.125c.621 0 1.125-.504 1.125-1.125V9.75M8.25 21h8.25"
      />
    </svg>
  );
}

function ListIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      fill="none"
      viewBox="0 0 24 24"
      strokeWidth={1.5}
      stroke="currentColor"
    >
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M8.25 6.75h12M8.25 12h12m-12 5.25h12M3.75 6.75h.007v.008H3.75V6.75Zm.375 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0ZM3.75 12h.007v.008H3.75V12Zm.375 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm-.375 5.25h.007v.008H3.75v-.008Zm.375 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Z"
      />
    </svg>
  );
}

function PlusIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      fill="none"
      viewBox="0 0 24 24"
      strokeWidth={2}
      stroke="currentColor"
    >
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
    </svg>
  );
}

function UsersIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      fill="none"
      viewBox="0 0 24 24"
      strokeWidth={1.5}
      stroke="currentColor"
    >
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M18 18.72a9.094 9.094 0 0 0 3.741-.479 3 3 0 0 0-4.682-2.72m.94 3.198.001.031c0 .225-.012.447-.037.666A11.944 11.944 0 0 1 12 21c-2.17 0-4.207-.576-5.963-1.584A6.062 6.062 0 0 1 6 18.719m12 0a5.971 5.971 0 0 0-.941-3.197m0 0A5.995 5.995 0 0 0 12 12.75a5.995 5.995 0 0 0-5.058 2.772m0 0a3 3 0 0 0-4.681 2.72 8.986 8.986 0 0 0 3.74.477m.94-3.197a5.971 5.971 0 0 0-.94 3.197M15 6.75a3 3 0 1 1-6 0 3 3 0 0 1 6 0Zm6 3a2.25 2.25 0 1 1-4.5 0 2.25 2.25 0 0 1 4.5 0Zm-13.5 0a2.25 2.25 0 1 1-4.5 0 2.25 2.25 0 0 1 4.5 0Z"
      />
    </svg>
  );
}

function UserIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      fill="none"
      viewBox="0 0 24 24"
      strokeWidth={1.5}
      stroke="currentColor"
    >
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M15.75 6a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0ZM4.501 20.118a7.5 7.5 0 0 1 14.998 0A17.933 17.933 0 0 1 12 21.75c-2.676 0-5.216-.584-7.499-1.632Z"
      />
    </svg>
  );
}

interface NavItemProps {
  to: string;
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  end?: boolean;
}

function NavItem({ to, icon: Icon, label, end }: NavItemProps) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        `flex flex-col items-center justify-center gap-1 px-3 py-2 text-xs font-medium transition-colors ${
          isActive
            ? 'text-indigo-600 dark:text-indigo-400'
            : 'text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200'
        }`
      }
    >
      <Icon className="h-6 w-6" />
      <span className="md:hidden">{label}</span>
      <span className="hidden md:inline">{label}</span>
    </NavLink>
  );
}

interface AddButtonProps {
  onAddList: () => void;
  onAddHousehold: () => void;
  showListOption: boolean;
  showHouseholdOption: boolean;
}

function AddButton({
  onAddList,
  onAddHousehold,
  showListOption,
  showHouseholdOption,
}: AddButtonProps) {
  const { t } = useTranslation();
  const [isOpen, setIsOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);

  // Close menu when clicking outside
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (
        menuRef.current &&
        !menuRef.current.contains(event.target as Node) &&
        buttonRef.current &&
        !buttonRef.current.contains(event.target as Node)
      ) {
        setIsOpen(false);
      }
    }

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      return () => document.removeEventListener('mousedown', handleClickOutside);
    }
  }, [isOpen]);

  // Close menu on escape key
  useEffect(() => {
    function handleEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setIsOpen(false);
      }
    }

    if (isOpen) {
      document.addEventListener('keydown', handleEscape);
      return () => document.removeEventListener('keydown', handleEscape);
    }
  }, [isOpen]);

  const handleAddList = () => {
    setIsOpen(false);
    onAddList();
  };

  const handleAddHousehold = () => {
    setIsOpen(false);
    onAddHousehold();
  };

  const showMenu = showListOption || showHouseholdOption;

  return (
    <div className="relative flex items-center justify-center">
      <button
        ref={buttonRef}
        onClick={() => (showMenu ? setIsOpen(!isOpen) : onAddList())}
        className="flex h-12 w-12 items-center justify-center rounded-full bg-indigo-600 text-white shadow-lg transition-transform hover:bg-indigo-700 hover:scale-105 active:scale-95 md:h-10 md:w-10"
        aria-label={t('nav.addNewItem')}
        aria-expanded={isOpen}
        aria-haspopup="true"
      >
        <PlusIcon className="h-6 w-6 md:h-5 md:w-5" />
      </button>

      {/* Dropdown menu */}
      {isOpen && showMenu && (
        <div
          ref={menuRef}
          className="absolute bottom-full mb-2 w-48 rounded-lg bg-white py-2 shadow-lg ring-1 ring-black ring-opacity-5 dark:bg-gray-800 dark:ring-gray-700 md:bottom-auto md:left-full md:mb-0 md:ml-2 md:top-1/2 md:-translate-y-1/2"
          role="menu"
          aria-orientation="vertical"
        >
          {showListOption && (
            <button
              onClick={handleAddList}
              className="flex w-full items-center gap-3 px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 dark:text-gray-200 dark:hover:bg-gray-700"
              role="menuitem"
            >
              <ListIcon className="h-5 w-5 text-gray-400" />
              {t('nav.newShoppingList')}
            </button>
          )}
          {showHouseholdOption && (
            <button
              onClick={handleAddHousehold}
              className="flex w-full items-center gap-3 px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 dark:text-gray-200 dark:hover:bg-gray-700"
              role="menuitem"
            >
              <UsersIcon className="h-5 w-5 text-gray-400" />
              {t('nav.newHousehold')}
            </button>
          )}
        </div>
      )}
    </div>
  );
}

export interface BottomNavProps {
  onAddList?: () => void;
  onAddHousehold?: () => void;
}

export default function BottomNav({ onAddList, onAddHousehold }: BottomNavProps) {
  const { t } = useTranslation();
  const location = useLocation();
  const navigate = useNavigate();

  // Determine context-aware options for add button
  const isOnListsPage = location.pathname === '/lists' || location.pathname.startsWith('/lists/');
  const isOnHouseholdsPage =
    location.pathname === '/households' || location.pathname.startsWith('/households/');

  // Default handlers if not provided
  const handleAddList = onAddList ?? (() => navigate('/lists?action=create'));
  const handleAddHousehold = onAddHousehold ?? (() => navigate('/households?action=create'));

  // Show context-aware options
  // On lists page: prioritize list creation
  // On households page: prioritize household creation
  // Elsewhere: show both options
  const showListOption = !isOnHouseholdsPage || !isOnListsPage;
  const showHouseholdOption = !isOnListsPage || !isOnHouseholdsPage;

  return (
    <>
      {/* Mobile bottom navigation */}
      <nav
        className="fixed inset-x-0 bottom-0 z-50 border-t border-gray-200 bg-white pb-safe dark:border-gray-700 dark:bg-gray-800 md:hidden"
        aria-label="Main navigation"
      >
        <div className="flex items-center justify-around">
          <NavItem to="/" icon={HomeIcon} label={t('nav.home')} end />
          <NavItem to="/lists" icon={ListIcon} label={t('nav.lists')} />
          <AddButton
            onAddList={handleAddList}
            onAddHousehold={handleAddHousehold}
            showListOption={showListOption}
            showHouseholdOption={showHouseholdOption}
          />
          <NavItem to="/households" icon={UsersIcon} label={t('nav.households')} />
          <NavItem to="/settings" icon={UserIcon} label={t('nav.profile')} />
        </div>
      </nav>

      {/* Desktop sidebar */}
      <nav
        className="fixed inset-y-0 left-0 z-40 hidden w-20 flex-col border-r border-gray-200 bg-white pt-20 dark:border-gray-700 dark:bg-gray-800 md:flex"
        aria-label="Main navigation"
      >
        <div className="flex flex-1 flex-col items-center gap-2 py-4">
          <NavItem to="/" icon={HomeIcon} label={t('nav.home')} end />
          <NavItem to="/lists" icon={ListIcon} label={t('nav.lists')} />
          <div className="my-2">
            <AddButton
              onAddList={handleAddList}
              onAddHousehold={handleAddHousehold}
              showListOption={showListOption}
              showHouseholdOption={showHouseholdOption}
            />
          </div>
          <NavItem to="/households" icon={UsersIcon} label={t('nav.households')} />
          <NavItem to="/settings" icon={UserIcon} label={t('nav.profile')} />
        </div>
      </nav>
    </>
  );
}
