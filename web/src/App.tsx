import { useEffect } from 'react';
import { BrowserRouter, Routes, Route, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAppDispatch, useAppSelector } from './store/hooks';
import { loginStart, loginSuccess, loginFailure } from './store/authSlice';
import { setHouseholds, setLoading } from './store/householdsSlice';
import {
  initWebSocketBridge,
  connectWebSocket,
  disconnectWebSocket,
  setCurrentUserId,
  setToastCallback,
} from './services/websocketBridge';
import { apiFetch } from './services/api';
import useTheme from './hooks/useTheme';
import { useToast } from './components/common';
import { AuthCallback, ProtectedRoute } from './components/auth';
import { MainLayout } from './components/layout';
import {
  DashboardPage,
  SettingsPage,
  ListsPage,
  ListDetailPage,
  HouseholdsPage,
  HouseholdDetailPage,
  LoginPage,
} from './components/pages';
import { SharedListView } from './components/shared';
import { store } from './store/store';

function SharedListPage() {
  const { t } = useTranslation();
  const { token } = useParams<{ token: string }>();
  if (!token) return <div>{t('shared.invalidSharedLink')}</div>;
  return (
    <div className="min-h-screen bg-gray-100">
      <header className="bg-white shadow">
        <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
          <h1 className="text-3xl font-bold tracking-tight text-gray-900">
            {t('shared.sharedShoppingList')}
          </h1>
        </div>
      </header>
      <main>
        <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
          <SharedListView token={token} />
        </div>
      </main>
    </div>
  );
}

function AppRoutes() {
  const dispatch = useAppDispatch();
  const { isAuthenticated, token } = useAppSelector((state) => state.auth);

  useTheme();

  // Initialize auth state from localStorage
  useEffect(() => {
    const storedToken = localStorage.getItem('token');
    if (storedToken) {
      dispatch(loginStart());
      apiFetch('/auth/me')
        .then((res) => {
          if (!res.ok) throw new Error('Invalid token');
          return res.json();
        })
        .then((user) => {
          const currentToken = localStorage.getItem('token') || storedToken;
          dispatch(loginSuccess({ user, token: currentToken }));
        })
        .catch(() => {
          localStorage.removeItem('token');
          localStorage.removeItem('refreshToken');
          dispatch(loginFailure('Session expired'));
        });
    }
  }, [dispatch]);

  // Wire up toast notifications for the WebSocket bridge
  const { showToast } = useToast();
  useEffect(() => {
    setToastCallback((message: string) => showToast({ message }));
    return () => setToastCallback(null);
  }, [showToast]);

  // Initialize WebSocket bridge and manage connection based on auth state
  useEffect(() => {
    initWebSocketBridge(store.dispatch, store.getState);
  }, []);

  const user = useAppSelector((state) => state.auth.user);

  useEffect(() => {
    if (isAuthenticated && token && user) {
      setCurrentUserId(user.id);
      connectWebSocket(token);
    } else {
      setCurrentUserId(null);
      disconnectWebSocket();
    }
  }, [isAuthenticated, token, user]);

  // Fetch households when authenticated
  useEffect(() => {
    if (isAuthenticated && token) {
      dispatch(setLoading(true));
      apiFetch('/households')
        .then((res) => res.json())
        .then((data) => dispatch(setHouseholds(data)))
        .catch(console.error)
        .finally(() => dispatch(setLoading(false)));
    }
  }, [isAuthenticated, token, dispatch]);

  return (
    <Routes>
      {/* Public routes */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/auth/callback" element={<AuthCallback />} />
      <Route path="/shared/:token" element={<SharedListPage />} />

      {/* Protected routes with layout */}
      <Route
        element={
          <ProtectedRoute>
            <MainLayout />
          </ProtectedRoute>
        }
      >
        <Route path="/" element={<DashboardPage />} />
        <Route path="/lists" element={<ListsPage />} />
        <Route path="/lists/:id" element={<ListDetailPage />} />
        <Route path="/households" element={<HouseholdsPage />} />
        <Route path="/households/:id" element={<HouseholdDetailPage />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Route>
    </Routes>
  );
}

function App() {
  return (
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  );
}

export default App;
