import { useTranslation } from 'react-i18next';
import { useAppSelector } from '../../store/hooks';
import { selectUIConnectionState, type UIConnectionState } from '../../store/websocketSlice';

interface ConnectionStatusProps {
  className?: string;
}

const statusConfig: Record<
  UIConnectionState,
  { dotColor: string; textKey: string | null; showSpinner: boolean }
> = {
  CONNECTING: {
    dotColor: '',
    textKey: null,
    showSpinner: true,
  },
  CONNECTED: {
    dotColor: 'bg-green-500',
    textKey: null,
    showSpinner: false,
  },
  RECONNECTING: {
    dotColor: 'bg-yellow-500',
    textKey: 'connection.reconnecting',
    showSpinner: false,
  },
  OFFLINE: {
    dotColor: 'bg-red-500',
    textKey: 'connection.offline',
    showSpinner: false,
  },
};

export default function ConnectionStatus({ className = '' }: ConnectionStatusProps) {
  const { t } = useTranslation();
  const connectionState = useAppSelector(selectUIConnectionState);
  const config = statusConfig[connectionState];

  return (
    <div
      className={`flex items-center gap-1.5 ${className}`}
      data-testid="connection-status"
      aria-label={t('connection.statusLabel', { state: connectionState.toLowerCase() })}
    >
      {config.showSpinner ? (
        <div
          data-testid="connection-spinner"
          className="h-3 w-3 animate-spin rounded-full border-2 border-gray-400 border-t-transparent"
          aria-hidden="true"
        />
      ) : (
        <div
          data-testid="connection-dot"
          className={`h-2.5 w-2.5 rounded-full ${config.dotColor}`}
          aria-hidden="true"
        />
      )}
      {config.textKey && (
        <span className="text-xs text-gray-500" data-testid="connection-text">
          {t(config.textKey)}
        </span>
      )}
    </div>
  );
}
