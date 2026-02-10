import { useCallback, useState, useEffect, useRef, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { ToastContext, type Toast } from './ToastContext';

// Default duration for toast auto-dismiss (5 seconds)
const DEFAULT_DURATION = 5000;

// Individual Toast Component
interface ToastItemProps {
  toast: Toast;
  onDismiss: (id: string) => void;
}

function ToastItem({ toast, onDismiss }: ToastItemProps) {
  const { t } = useTranslation();
  const [isExiting, setIsExiting] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout>>(undefined);

  const handleDismiss = useCallback(() => {
    setIsExiting(true);
    // Wait for exit animation before removing
    setTimeout(() => onDismiss(toast.id), 150);
  }, [toast.id, onDismiss]);

  const handleUndo = useCallback(() => {
    if (toast.onUndo) {
      toast.onUndo();
    }
    handleDismiss();
  }, [toast, handleDismiss]);

  // Auto-dismiss after duration
  useEffect(() => {
    const duration = toast.duration ?? DEFAULT_DURATION;
    timerRef.current = setTimeout(handleDismiss, duration);

    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
    };
  }, [toast.duration, handleDismiss]);

  // Pause timer on hover
  const handleMouseEnter = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
    }
  }, []);

  const handleMouseLeave = useCallback(() => {
    const duration = toast.duration ?? DEFAULT_DURATION;
    timerRef.current = setTimeout(handleDismiss, duration);
  }, [toast.duration, handleDismiss]);

  return (
    <div
      role="alert"
      aria-live="polite"
      data-testid="toast-item"
      className={`flex items-center justify-between gap-3 rounded-lg bg-gray-800 px-4 py-3 text-white shadow-lg transition-all duration-150 ${
        isExiting ? 'translate-y-2 opacity-0' : 'translate-y-0 opacity-100'
      }`}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
    >
      <span className="text-sm" data-testid="toast-message">
        {toast.message}
      </span>
      <div className="flex items-center gap-2">
        {toast.onUndo && (
          <button
            onClick={handleUndo}
            className="text-sm font-medium text-blue-400 hover:text-blue-300 focus:outline-none focus:ring-2 focus:ring-blue-400 focus:ring-offset-2 focus:ring-offset-gray-800"
            data-testid="toast-undo-button"
          >
            {t('common.undo')}
          </button>
        )}
        <button
          onClick={handleDismiss}
          className="text-gray-400 hover:text-gray-200 focus:outline-none focus:ring-2 focus:ring-gray-400 focus:ring-offset-2 focus:ring-offset-gray-800"
          aria-label={t('common.dismiss')}
          data-testid="toast-dismiss-button"
        >
          <svg
            className="h-4 w-4"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            aria-hidden="true"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M6 18L18 6M6 6l12 12"
            />
          </svg>
        </button>
      </div>
    </div>
  );
}

// Toast Container Component
interface ToastContainerProps {
  toasts: Toast[];
  onDismiss: (id: string) => void;
}

function ToastContainer({ toasts, onDismiss }: ToastContainerProps) {
  const { t } = useTranslation();

  if (toasts.length === 0) return null;

  return (
    <div
      className="fixed bottom-20 left-4 right-4 z-50 flex flex-col gap-2 sm:bottom-6 sm:left-auto sm:right-6 sm:w-96"
      data-testid="toast-container"
      aria-label={t('common.notifications')}
    >
      {toasts.map((toast) => (
        <ToastItem key={toast.id} toast={toast} onDismiss={onDismiss} />
      ))}
    </div>
  );
}

// Provider Component
interface ToastProviderProps {
  children: ReactNode;
  maxToasts?: number;
}

export default function ToastProvider({ children, maxToasts = 5 }: ToastProviderProps) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const toastIdRef = useRef(0);

  const showToast = useCallback(
    (toast: Omit<Toast, 'id'>) => {
      const id = `toast-${++toastIdRef.current}`;
      const newToast: Toast = { ...toast, id };

      setToasts((prev) => {
        // Limit the number of toasts
        const updated = [...prev, newToast];
        if (updated.length > maxToasts) {
          return updated.slice(-maxToasts);
        }
        return updated;
      });

      return id;
    },
    [maxToasts]
  );

  const dismissToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
  }, []);

  const dismissAll = useCallback(() => {
    setToasts([]);
  }, []);

  return (
    <ToastContext.Provider value={{ showToast, dismissToast, dismissAll }}>
      {children}
      <ToastContainer toasts={toasts} onDismiss={dismissToast} />
    </ToastContext.Provider>
  );
}
