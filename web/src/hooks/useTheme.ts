import { useEffect, useState } from 'react';
import { useAppSelector } from '../store/hooks';

type Theme = 'system' | 'light' | 'dark';

function applyTheme(theme: Theme) {
  const prefersDark =
    theme === 'dark' ||
    (theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);

  document.documentElement.classList.toggle('dark', prefersDark);
}

export default function useTheme() {
  const token = useAppSelector((state) => state.auth.token);
  const [theme, setTheme] = useState<Theme>(() => {
    return (localStorage.getItem('theme') as Theme) || 'system';
  });

  // Fetch theme preference from backend when authenticated
  useEffect(() => {
    if (!token) return;

    fetch('http://localhost:8080/api/preferences', {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((res) => {
        if (!res.ok) throw new Error('Failed to fetch preferences');
        return res.json();
      })
      .then((data) => {
        if (data.theme) {
          setTheme(data.theme);
          localStorage.setItem('theme', data.theme);
        }
      })
      .catch(() => {
        // Ignore — use cached or default
      });
  }, [token]);

  // Apply theme whenever it changes, and listen for system preference changes
  useEffect(() => {
    applyTheme(theme);

    if (theme !== 'system') return;

    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handler = () => applyTheme('system');
    mediaQuery.addEventListener('change', handler);
    return () => mediaQuery.removeEventListener('change', handler);
  }, [theme]);
}
