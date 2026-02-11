import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAppDispatch, useAppSelector } from '../../store/hooks';
import {
  fetchRecurringItems,
  createRecurringItem,
  updateRecurringItem,
  deleteRecurringItem,
  pauseRecurringItem,
  resumeRecurringItem,
  type RecurringItem,
} from '../../store/recurringItemsSlice';
import { LoadingSpinner, ErrorAlert } from '../common';

interface RecurringItemsListProps {
  householdId: string;
}

const FREQUENCY_LABELS: Record<string, string> = {
  DAILY: 'recurring.frequencyDaily',
  WEEKLY: 'recurring.frequencyWeekly',
  BIWEEKLY: 'recurring.frequencyBiweekly',
  MONTHLY: 'recurring.frequencyMonthly',
};

function formatFrequency(frequency: string, t: (key: string) => string): string {
  const key = FREQUENCY_LABELS[frequency];
  if (key) return t(key);
  return frequency.charAt(0) + frequency.slice(1).toLowerCase();
}

export default function RecurringItemsList({ householdId }: RecurringItemsListProps) {
  const { t } = useTranslation();
  const dispatch = useAppDispatch();
  const { items, isLoading, error } = useAppSelector((state) => state.recurringItems);
  const [showForm, setShowForm] = useState(false);
  const [editingItem, setEditingItem] = useState<RecurringItem | null>(null);
  const [showPauseDialog, setShowPauseDialog] = useState<string | null>(null);
  const [pauseUntil, setPauseUntil] = useState('');
  const [showDeleteDialog, setShowDeleteDialog] = useState<string | null>(null);

  const [formName, setFormName] = useState('');
  const [formQuantity, setFormQuantity] = useState('1');
  const [formUnit, setFormUnit] = useState('');
  const [formFrequency, setFormFrequency] = useState('WEEKLY');

  useEffect(() => {
    dispatch(fetchRecurringItems(householdId));
  }, [householdId, dispatch]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await dispatch(
        createRecurringItem({
          householdId,
          data: {
            name: formName.trim(),
            quantity: parseFloat(formQuantity) || 1,
            unit: formUnit.trim() || null,
            frequency: formFrequency,
          },
        })
      ).unwrap();
      resetForm();
    } catch {
      // Error state handled by slice
    }
  };

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingItem) return;
    try {
      await dispatch(
        updateRecurringItem({
          householdId,
          itemId: editingItem.id,
          data: {
            name: formName.trim(),
            quantity: parseFloat(formQuantity) || 1,
            unit: formUnit.trim() || null,
            frequency: formFrequency,
          },
        })
      ).unwrap();
      resetForm();
    } catch {
      // Error state handled by slice
    }
  };

  const handleDelete = async () => {
    if (!showDeleteDialog) return;
    try {
      await dispatch(deleteRecurringItem({ householdId, itemId: showDeleteDialog })).unwrap();
      setShowDeleteDialog(null);
    } catch {
      // Error state handled by slice
    }
  };

  const handlePause = async (itemId: string) => {
    try {
      await dispatch(
        pauseRecurringItem({ householdId, itemId, until: pauseUntil || null })
      ).unwrap();
      setShowPauseDialog(null);
      setPauseUntil('');
    } catch {
      // Error state handled by slice
    }
  };

  const handleResume = async (itemId: string) => {
    try {
      await dispatch(resumeRecurringItem({ householdId, itemId })).unwrap();
    } catch {
      // Error state handled by slice
    }
  };

  const startEdit = (item: RecurringItem) => {
    setEditingItem(item);
    setFormName(item.name);
    setFormQuantity(item.quantity.toString());
    setFormUnit(item.unit || '');
    setFormFrequency(item.frequency);
    setShowForm(true);
  };

  const resetForm = () => {
    setShowForm(false);
    setEditingItem(null);
    setFormName('');
    setFormQuantity('1');
    setFormUnit('');
    setFormFrequency('WEEKLY');
  };

  if (isLoading) {
    return (
      <div className="flex justify-center py-8">
        <LoadingSpinner />
      </div>
    );
  }

  return (
    <div className="mt-6">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white">
          {t('recurring.title')}
        </h3>
        <button
          onClick={() => {
            resetForm();
            setShowForm(true);
          }}
          className="rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white hover:bg-indigo-500"
        >
          {t('recurring.addItem')}
        </button>
      </div>

      {error && <ErrorAlert message={error} />}

      {showForm && (
        <form
          onSubmit={editingItem ? handleUpdate : handleCreate}
          className="mb-4 rounded-lg bg-white p-4 shadow dark:bg-gray-800"
        >
          <h4 className="mb-3 text-sm font-semibold text-gray-900 dark:text-white">
            {editingItem ? t('recurring.editItem') : t('recurring.newItem')}
          </h4>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <div className="col-span-2 sm:col-span-1">
              <label
                htmlFor="recurring-name"
                className="block text-sm font-medium text-gray-700 dark:text-gray-300"
              >
                {t('common.name')}
              </label>
              <input
                id="recurring-name"
                type="text"
                value={formName}
                onChange={(e) => setFormName(e.target.value)}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
                required
              />
            </div>
            <div>
              <label
                htmlFor="recurring-quantity"
                className="block text-sm font-medium text-gray-700 dark:text-gray-300"
              >
                {t('shoppingListView.qty')}
              </label>
              <input
                id="recurring-quantity"
                type="number"
                step="0.1"
                min="0.1"
                value={formQuantity}
                onChange={(e) => setFormQuantity(e.target.value)}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
              />
            </div>
            <div>
              <label
                htmlFor="recurring-unit"
                className="block text-sm font-medium text-gray-700 dark:text-gray-300"
              >
                {t('shoppingListView.unit')}
              </label>
              <input
                id="recurring-unit"
                type="text"
                value={formUnit}
                onChange={(e) => setFormUnit(e.target.value)}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
              />
            </div>
            <div>
              <label
                htmlFor="recurring-frequency"
                className="block text-sm font-medium text-gray-700 dark:text-gray-300"
              >
                {t('recurring.frequency')}
              </label>
              <select
                id="recurring-frequency"
                value={formFrequency}
                onChange={(e) => setFormFrequency(e.target.value)}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
              >
                <option value="DAILY">{t('recurring.frequencyDaily')}</option>
                <option value="WEEKLY">{t('recurring.frequencyWeekly')}</option>
                <option value="BIWEEKLY">{t('recurring.frequencyBiweekly')}</option>
                <option value="MONTHLY">{t('recurring.frequencyMonthly')}</option>
              </select>
            </div>
          </div>
          <div className="mt-3 flex gap-2">
            <button
              type="submit"
              className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500"
            >
              {editingItem ? t('common.save') : t('common.create')}
            </button>
            <button
              type="button"
              onClick={resetForm}
              className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
            >
              {t('common.cancel')}
            </button>
          </div>
        </form>
      )}

      {items.length === 0 ? (
        <div className="rounded-lg bg-white p-6 text-center shadow dark:bg-gray-800">
          <p className="text-gray-500 dark:text-gray-400">{t('recurring.noItems')}</p>
        </div>
      ) : (
        <div className="rounded-lg bg-white shadow dark:bg-gray-800">
          <ul className="divide-y divide-gray-200 dark:divide-gray-700">
            {items.map((item) => (
              <li key={item.id} className="flex items-center justify-between px-4 py-3">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <p
                      className={`text-sm font-medium ${item.isActive ? 'text-gray-900 dark:text-white' : 'text-gray-400 dark:text-gray-500'}`}
                    >
                      {item.name}
                    </p>
                    {!item.isActive && (
                      <span className="inline-flex items-center rounded-full bg-yellow-100 px-2 py-0.5 text-xs font-medium text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400">
                        {item.pausedUntil
                          ? t('recurring.pausedUntilDate', { date: item.pausedUntil })
                          : t('recurring.paused')}
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-gray-500 dark:text-gray-400">
                    {item.quantity}
                    {item.unit ? ` ${item.unit}` : ''} &middot; {formatFrequency(item.frequency, t)}
                    {item.lastPurchased &&
                      ` · ${t('recurring.lastPurchased', { date: item.lastPurchased })}`}
                  </p>
                </div>
                <div className="flex items-center gap-1">
                  {item.isActive ? (
                    <button
                      onClick={() => {
                        setShowPauseDialog(item.id);
                        setPauseUntil('');
                      }}
                      className="rounded p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
                      title={t('recurring.pause')}
                    >
                      <svg
                        xmlns="http://www.w3.org/2000/svg"
                        viewBox="0 0 20 20"
                        fill="currentColor"
                        className="h-4 w-4"
                      >
                        <path d="M5.75 3a.75.75 0 00-.75.75v12.5c0 .414.336.75.75.75h1.5a.75.75 0 00.75-.75V3.75A.75.75 0 007.25 3h-1.5zM12.75 3a.75.75 0 00-.75.75v12.5c0 .414.336.75.75.75h1.5a.75.75 0 00.75-.75V3.75a.75.75 0 00-.75-.75h-1.5z" />
                      </svg>
                    </button>
                  ) : (
                    <button
                      onClick={() => handleResume(item.id)}
                      className="rounded p-1.5 text-gray-400 hover:bg-green-50 hover:text-green-600 dark:hover:bg-green-900/20 dark:hover:text-green-400"
                      title={t('recurring.resume')}
                    >
                      <svg
                        xmlns="http://www.w3.org/2000/svg"
                        viewBox="0 0 20 20"
                        fill="currentColor"
                        className="h-4 w-4"
                      >
                        <path d="M6.3 2.841A1.5 1.5 0 004 4.11V15.89a1.5 1.5 0 002.3 1.269l9.344-5.89a1.5 1.5 0 000-2.538L6.3 2.84z" />
                      </svg>
                    </button>
                  )}
                  <button
                    onClick={() => startEdit(item)}
                    className="rounded p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
                    title={t('recurring.edit')}
                  >
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      viewBox="0 0 20 20"
                      fill="currentColor"
                      className="h-4 w-4"
                    >
                      <path d="M2.695 14.763l-1.262 3.154a.5.5 0 00.65.65l3.155-1.262a4 4 0 001.343-.885L17.5 5.5a2.121 2.121 0 00-3-3L3.58 13.42a4 4 0 00-.885 1.343z" />
                    </svg>
                  </button>
                  <button
                    onClick={() => setShowDeleteDialog(item.id)}
                    className="rounded p-1.5 text-gray-400 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-900/20 dark:hover:text-red-400"
                    title={t('common.delete')}
                  >
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      viewBox="0 0 20 20"
                      fill="currentColor"
                      className="h-4 w-4"
                    >
                      <path
                        fillRule="evenodd"
                        d="M8.75 1A2.75 2.75 0 006 3.75v.443c-.795.077-1.584.176-2.365.298a.75.75 0 10.23 1.482l.149-.022 1.005 11.36A2.75 2.75 0 007.77 20h4.46a2.75 2.75 0 002.75-2.689l1.006-11.36.148.022a.75.75 0 00.23-1.482A41.03 41.03 0 0014 4.193V3.75A2.75 2.75 0 0011.25 1h-2.5zM10 4c.84 0 1.673.025 2.5.075V3.75c0-.69-.56-1.25-1.25-1.25h-2.5c-.69 0-1.25.56-1.25 1.25v.325C8.327 4.025 9.16 4 10 4zM8.58 7.72a.75.75 0 00-1.5.06l.3 7.5a.75.75 0 101.5-.06l-.3-7.5zm4.34.06a.75.75 0 10-1.5-.06l-.3 7.5a.75.75 0 101.5.06l.3-7.5z"
                        clipRule="evenodd"
                      />
                    </svg>
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}

      {showPauseDialog && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
          onClick={() => {
            setShowPauseDialog(null);
            setPauseUntil('');
          }}
        >
          <div
            className="w-full max-w-sm rounded-lg bg-white p-6 shadow-xl dark:bg-gray-800"
            role="dialog"
            aria-label={t('recurring.pauseTitle')}
            onClick={(e) => e.stopPropagation()}
          >
            <h4 className="mb-4 text-lg font-semibold text-gray-900 dark:text-white">
              {t('recurring.pauseTitle')}
            </h4>
            <div className="mb-4">
              <label
                htmlFor="pause-until"
                className="block text-sm font-medium text-gray-700 dark:text-gray-300"
              >
                {t('recurring.pauseUntilLabel')}
              </label>
              <input
                id="pause-until"
                type="date"
                value={pauseUntil}
                onChange={(e) => setPauseUntil(e.target.value)}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
              />
              <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                {t('recurring.pauseUntilHint')}
              </p>
            </div>
            <div className="flex gap-2">
              <button
                onClick={() => handlePause(showPauseDialog)}
                className="rounded-md bg-yellow-600 px-4 py-2 text-sm font-semibold text-white hover:bg-yellow-500"
              >
                {t('recurring.pause')}
              </button>
              <button
                onClick={() => {
                  setShowPauseDialog(null);
                  setPauseUntil('');
                }}
                className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
              >
                {t('common.cancel')}
              </button>
            </div>
          </div>
        </div>
      )}

      {showDeleteDialog && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
          onClick={() => setShowDeleteDialog(null)}
        >
          <div
            className="w-full max-w-sm rounded-lg bg-white p-6 shadow-xl dark:bg-gray-800"
            role="dialog"
            aria-label={t('recurring.confirmDelete')}
            onClick={(e) => e.stopPropagation()}
          >
            <h4 className="mb-4 text-lg font-semibold text-gray-900 dark:text-white">
              {t('recurring.confirmDelete')}
            </h4>
            <p className="mb-4 text-sm text-gray-500 dark:text-gray-400">
              {t('recurring.confirmDeleteDescription')}
            </p>
            <div className="flex gap-2">
              <button
                onClick={handleDelete}
                className="rounded-md bg-red-600 px-4 py-2 text-sm font-semibold text-white hover:bg-red-500"
              >
                {t('common.delete')}
              </button>
              <button
                onClick={() => setShowDeleteDialog(null)}
                className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
              >
                {t('common.cancel')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
