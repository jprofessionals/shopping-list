import type { ReactNode } from 'react';

interface ErrorAlertProps {
  title?: string;
  message: string;
  children?: ReactNode;
  variant?: 'error' | 'warning';
}

const variantClasses = {
  error: {
    container: 'bg-red-50',
    title: 'text-red-700',
    message: 'text-red-600',
  },
  warning: {
    container: 'bg-yellow-50',
    title: 'text-yellow-700',
    message: 'text-yellow-600',
  },
};

export default function ErrorAlert({
  title,
  message,
  children,
  variant = 'error',
}: ErrorAlertProps) {
  const classes = variantClasses[variant];

  return (
    <div className={`rounded-md p-4 ${classes.container}`}>
      {title && <p className={`text-sm font-medium ${classes.title}`}>{title}</p>}
      <p className={`${title ? 'mt-1' : ''} text-sm ${classes.message}`}>{message}</p>
      {children}
    </div>
  );
}
