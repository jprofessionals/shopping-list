import { useEffect, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAppSelector } from '../../store/hooks';
import { Login } from '../auth';
import { LoadingSpinner } from '../common';

interface AuthConfig {
  googleEnabled: boolean;
  localEnabled: boolean;
  googleClientId: string | null;
}

export default function LoginPage() {
  const { t } = useTranslation();
  const [authConfig, setAuthConfig] = useState<AuthConfig | null>(null);
  const [isLoadingConfig, setIsLoadingConfig] = useState(true);
  const { isAuthenticated, isLoading: isAuthLoading } = useAppSelector((state) => state.auth);
  const location = useLocation();

  useEffect(() => {
    fetch('http://localhost:8080/auth/config')
      .then((res) => res.json())
      .then(setAuthConfig)
      .catch(console.error)
      .finally(() => setIsLoadingConfig(false));
  }, []);

  // If already authenticated, redirect to intended destination or home
  if (isAuthenticated && !isAuthLoading) {
    const from = location.state?.from?.pathname || '/';
    return <Navigate to={from} replace />;
  }

  if (isLoadingConfig || isAuthLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-100 dark:bg-gray-900">
        <LoadingSpinner />
      </div>
    );
  }

  if (!authConfig) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-100 dark:bg-gray-900">
        <div className="text-red-600">{t('auth.login.failedToLoadConfig')}</div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-100 dark:bg-gray-900">
      <Login authConfig={authConfig} />
    </div>
  );
}
