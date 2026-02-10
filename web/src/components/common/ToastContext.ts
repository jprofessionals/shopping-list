import { createContext } from 'react';

// Types
export interface Toast {
  id: string;
  message: string;
  onUndo?: () => void;
  duration?: number;
}

export interface ToastContextValue {
  showToast: (toast: Omit<Toast, 'id'>) => string;
  dismissToast: (id: string) => void;
  dismissAll: () => void;
}

// Context
export const ToastContext = createContext<ToastContextValue | null>(null);
