import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAppDispatch, useAppSelector } from '../../store/hooks';
import { addList, setError } from '../../store/listsSlice';
import { Modal, ModalActions, PrimaryButton, SecondaryButton } from '../common';

interface CreateListModalProps {
  onClose: () => void;
}

export default function CreateListModal({ onClose }: CreateListModalProps) {
  const { t } = useTranslation();
  const [name, setName] = useState('');
  const [householdId, setHouseholdId] = useState('');
  const [isPersonal, setIsPersonal] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const dispatch = useAppDispatch();
  const token = useAppSelector((state) => state.auth.token);
  const households = useAppSelector((state) => state.households.items);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim() || !token) return;

    setIsSubmitting(true);
    try {
      const response = await fetch('http://localhost:8080/lists', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          name: name.trim(),
          householdId: householdId || null,
          // Personal list = no household selected, or explicitly marked as private within a household
          isPersonal: !householdId || isPersonal,
        }),
      });

      if (!response.ok) {
        throw new Error('Failed to create shopping list');
      }

      const list = await response.json();
      dispatch(addList(list));
      setName('');
      setHouseholdId('');
      setIsPersonal(false);
      onClose();
    } catch (err) {
      dispatch(setError(err instanceof Error ? err.message : 'Failed to create shopping list'));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal isOpen={true} onClose={onClose} title={t('createList.title')}>
      <form onSubmit={handleSubmit}>
        <div className="space-y-4">
          <div>
            <label htmlFor="listName" className="block text-sm font-medium leading-6 text-gray-900">
              {t('createList.listName')}
            </label>
            <input
              type="text"
              id="listName"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder={t('createList.listNamePlaceholder')}
              className="mt-2 block w-full rounded-md border-0 px-3 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
              required
            />
          </div>
          <div>
            <label
              htmlFor="household"
              className="block text-sm font-medium leading-6 text-gray-900"
            >
              {t('createList.household')}
            </label>
            <select
              id="household"
              value={householdId}
              onChange={(e) => {
                setHouseholdId(e.target.value);
                if (!e.target.value) {
                  setIsPersonal(false);
                }
              }}
              className="mt-2 block w-full rounded-md border-0 px-3 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
            >
              <option value="">{t('createList.personalNoHousehold')}</option>
              {households.map((household) => (
                <option key={household.id} value={household.id}>
                  {household.name}
                </option>
              ))}
            </select>
          </div>
          {householdId && (
            <div>
              <label className="flex items-center">
                <input
                  type="checkbox"
                  checked={isPersonal}
                  onChange={(e) => setIsPersonal(e.target.checked)}
                  className="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-600"
                />
                <span className="ml-2 text-sm text-gray-900">{t('createList.private')}</span>
              </label>
              <p className="mt-1 text-xs text-gray-500">{t('createList.privateDescription')}</p>
            </div>
          )}
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
