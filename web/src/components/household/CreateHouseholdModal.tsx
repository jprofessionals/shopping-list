import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAppDispatch, useAppSelector } from '../../store/hooks';
import { addHousehold, setError } from '../../store/householdsSlice';
import { Modal, ModalActions, PrimaryButton, SecondaryButton } from '../common';

interface CreateHouseholdModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function CreateHouseholdModal({ isOpen, onClose }: CreateHouseholdModalProps) {
  const { t } = useTranslation();
  const [name, setName] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const dispatch = useAppDispatch();
  const token = useAppSelector((state) => state.auth.token);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim() || !token) return;

    setIsSubmitting(true);
    try {
      const response = await fetch('http://localhost:8080/api/households', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ name: name.trim() }),
      });

      if (!response.ok) {
        throw new Error('Failed to create household');
      }

      const household = await response.json();
      dispatch(addHousehold(household));
      setName('');
      onClose();
    } catch (err) {
      dispatch(setError(err instanceof Error ? err.message : 'Failed to create household'));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('createHousehold.title')}>
      <form onSubmit={handleSubmit}>
        <div>
          <label
            htmlFor="householdName"
            className="block text-sm font-medium leading-6 text-gray-900"
          >
            {t('createHousehold.householdName')}
          </label>
          <input
            type="text"
            id="householdName"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder={t('createHousehold.namePlaceholder')}
            className="mt-2 block w-full rounded-md border-0 px-3 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
            required
          />
        </div>
        <ModalActions>
          <PrimaryButton type="submit" disabled={isSubmitting || !name.trim()}>
            {isSubmitting ? t('common.creating') : t('common.create')}
          </PrimaryButton>
          <SecondaryButton onClick={onClose}>{t('common.cancel')}</SecondaryButton>
        </ModalActions>
      </form>
    </Modal>
  );
}
