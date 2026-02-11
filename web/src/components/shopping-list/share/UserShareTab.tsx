import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAppSelector } from '../../../store/hooks';
import { ErrorAlert, ModalActions, PrimaryButton, SecondaryButton } from '../../common';
import { apiFetch } from '../../../services/api';

type Permission = 'READ' | 'CHECK' | 'WRITE';

interface UserShareTabProps {
  listId: string;
  onClose: () => void;
}

export default function UserShareTab({ listId, onClose }: UserShareTabProps) {
  const { t } = useTranslation();
  const [email, setEmail] = useState('');
  const [permission, setPermission] = useState<Permission>('READ');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const token = useAppSelector((state) => state.auth.token);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim() || !token) return;

    setIsSubmitting(true);
    setError(null);
    try {
      const response = await apiFetch(`/lists/${listId}/shares`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          type: 'USER',
          email: email.trim(),
          permission,
        }),
      });

      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        throw new Error(data.error || 'Failed to share list');
      }

      setEmail('');
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to share list');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      {error && (
        <div className="mb-4">
          <ErrorAlert message={error} />
        </div>
      )}

      <div className="space-y-4">
        <div>
          <label htmlFor="email" className="block text-sm font-medium leading-6 text-gray-900">
            {t('userShare.email')}
          </label>
          <input
            type="email"
            id="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder={t('userShare.emailPlaceholder')}
            className="mt-2 block w-full rounded-md border-0 px-3 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
            required
          />
        </div>
        <div>
          <label htmlFor="permission" className="block text-sm font-medium leading-6 text-gray-900">
            {t('userShare.permission')}
          </label>
          <select
            id="permission"
            value={permission}
            onChange={(e) => setPermission(e.target.value as Permission)}
            className="mt-2 block w-full rounded-md border-0 px-3 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
          >
            <option value="READ">{t('userShare.readOnly')}</option>
            <option value="CHECK">{t('userShare.canCheckItems')}</option>
            <option value="WRITE">{t('userShare.canEdit')}</option>
          </select>
        </div>
      </div>
      <ModalActions>
        <PrimaryButton type="submit" disabled={isSubmitting || !email.trim()}>
          {isSubmitting ? t('userShare.sharing') : t('userShare.shareButton')}
        </PrimaryButton>
        <SecondaryButton onClick={onClose}>{t('common.cancel')}</SecondaryButton>
      </ModalActions>
    </form>
  );
}
