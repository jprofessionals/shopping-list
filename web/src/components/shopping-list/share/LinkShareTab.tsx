import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAppSelector } from '../../../store/hooks';
import { ErrorAlert, ModalActions, PrimaryButton, SecondaryButton } from '../../common';
import { apiFetch } from '../../../services/api';
import LinkDisplay from './LinkDisplay';

type Permission = 'READ' | 'CHECK' | 'WRITE';

const EXPIRY_PRESETS = [
  { label: '1h', hours: 1 },
  { label: '6h', hours: 6 },
  { label: '24h', hours: 24 },
  { label: '3d', hours: 72 },
  { label: '7d', hours: 168 },
];

interface LinkShareTabProps {
  listId: string;
  onClose: () => void;
}

export default function LinkShareTab({ listId, onClose }: LinkShareTabProps) {
  const { t } = useTranslation();
  const [permission, setPermission] = useState<Permission>('READ');
  const [expirationHours, setExpirationHours] = useState(24);
  const [generatedLink, setGeneratedLink] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const token = useAppSelector((state) => state.auth.token);

  const handleCreateLink = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token) return;

    setIsSubmitting(true);
    setError(null);
    try {
      const response = await apiFetch(`/lists/${listId}/shares`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          type: 'LINK',
          permission,
          expirationHours,
        }),
      });

      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        throw new Error(data.error || 'Failed to create share link');
      }

      const data = await response.json();
      const shareUrl = `${window.location.origin}/shared/${data.linkToken}`;
      setGeneratedLink(shareUrl);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create share link');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (generatedLink) {
    return <LinkDisplay link={generatedLink} onClose={onClose} />;
  }

  return (
    <form onSubmit={handleCreateLink}>
      {error && (
        <div className="mb-4">
          <ErrorAlert message={error} />
        </div>
      )}

      <div className="space-y-4">
        <div>
          <label
            htmlFor="linkPermission"
            className="block text-sm font-medium leading-6 text-gray-900"
          >
            {t('linkShare.permission')}
          </label>
          <select
            id="linkPermission"
            value={permission}
            onChange={(e) => setPermission(e.target.value as Permission)}
            className="mt-2 block w-full rounded-md border-0 px-3 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
          >
            <option value="READ">{t('linkShare.readOnly')}</option>
            <option value="CHECK">{t('linkShare.canCheckItems')}</option>
            <option value="WRITE">{t('linkShare.canEdit')}</option>
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium leading-6 text-gray-900">
            {t('linkShare.expiresIn')}
          </label>
          <div className="mt-2 flex gap-2">
            {EXPIRY_PRESETS.map((preset) => (
              <button
                key={preset.hours}
                type="button"
                onClick={() => setExpirationHours(preset.hours)}
                className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
                  expirationHours === preset.hours
                    ? 'bg-indigo-600 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
              >
                {preset.label}
              </button>
            ))}
          </div>
        </div>
      </div>
      <ModalActions>
        <PrimaryButton type="submit" disabled={isSubmitting}>
          {isSubmitting ? t('linkShare.creatingLink') : t('linkShare.createLink')}
        </PrimaryButton>
        <SecondaryButton onClick={onClose}>{t('common.cancel')}</SecondaryButton>
      </ModalActions>
    </form>
  );
}
