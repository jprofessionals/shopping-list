import { useEffect } from 'react';
import { useAppDispatch } from '../../store/hooks';
import { loginStart, loginSuccess, loginFailure } from '../../store/authSlice';
import { apiFetch } from '../../services/api';
import { LoadingSpinner } from '../common';

export default function AuthCallback() {
  const dispatch = useAppDispatch();

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const token = params.get('token');
    const refreshToken = params.get('refreshToken');
    const error = params.get('error');

    if (error) {
      dispatch(loginFailure(error));
      window.location.href = '/login?error=' + error;
      return;
    }

    if (token) {
      dispatch(loginStart());
      localStorage.setItem('token', token);
      if (refreshToken) {
        localStorage.setItem('refreshToken', refreshToken);
      }

      apiFetch('/auth/me')
        .then((res) => {
          if (!res.ok) throw new Error('Failed to fetch user');
          return res.json();
        })
        .then((user) => {
          dispatch(loginSuccess({ user, token }));
          window.location.href = '/';
        })
        .catch((err) => {
          dispatch(loginFailure(err.message));
          window.location.href = '/login?error=auth_failed';
        });
    }
  }, [dispatch]);

  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="text-center">
        <div className="mx-auto mb-4">
          <LoadingSpinner />
        </div>
        <p className="text-gray-600">Completing sign in...</p>
      </div>
    </div>
  );
}
