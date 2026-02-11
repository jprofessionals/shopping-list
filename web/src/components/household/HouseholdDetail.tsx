import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAppSelector, useAppDispatch } from '../../store/hooks';
import { removeHousehold } from '../../store/householdsSlice';
import { LoadingSpinner, ErrorAlert, Badge } from '../common';
import { apiFetch } from '../../services/api';
import CommentFeed from '../comments/CommentFeed';
import { RecurringItemsList } from '../recurring';

interface Member {
  accountId: string;
  email: string;
  displayName: string;
  avatarUrl: string | null;
  role: string;
  joinedAt: string;
}

interface HouseholdDetailData {
  id: string;
  name: string;
  createdAt: string;
  members: Member[];
}

interface HouseholdDetailProps {
  householdId: string;
  onBack: () => void;
}

export default function HouseholdDetail({ householdId, onBack }: HouseholdDetailProps) {
  const { t } = useTranslation();
  const [household, setHousehold] = useState<HouseholdDetailData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showAddMember, setShowAddMember] = useState(false);
  const [addEmail, setAddEmail] = useState('');
  const [addRole, setAddRole] = useState('MEMBER');

  const roleLabel = (role: string) =>
    role === 'OWNER' ? t('householdDetail.roleOwner') : t('householdDetail.roleMember');
  const [addError, setAddError] = useState<string | null>(null);
  const [isAdding, setIsAdding] = useState(false);
  const token = useAppSelector((state) => state.auth.token);
  const currentUser = useAppSelector((state) => state.auth.user);
  const dispatch = useAppDispatch();

  const fetchHousehold = useCallback(async () => {
    if (!token) return;

    try {
      const response = await apiFetch(`/households/${householdId}`);

      if (!response.ok) throw new Error('Failed to fetch household');

      const data = await response.json();
      setHousehold(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An error occurred');
    } finally {
      setIsLoading(false);
    }
  }, [token, householdId]);

  useEffect(() => {
    fetchHousehold();
  }, [fetchHousehold]);

  const handleDelete = async () => {
    if (!token || !confirm(t('householdDetail.confirmDelete'))) return;

    try {
      const response = await apiFetch(`/households/${householdId}`, {
        method: 'DELETE',
      });

      if (!response.ok) throw new Error('Failed to delete household');

      dispatch(removeHousehold(householdId));
      onBack();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete');
    }
  };

  const handleAddMember = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token || !addEmail.trim()) return;

    setIsAdding(true);
    setAddError(null);

    try {
      const response = await apiFetch(`/households/${householdId}/members`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: addEmail.trim(), role: addRole }),
      });

      if (response.status === 404) {
        setAddError(t('householdDetail.userNotFound'));
        return;
      }
      if (response.status === 409) {
        setAddError(t('householdDetail.alreadyMember'));
        return;
      }
      if (!response.ok) throw new Error('Failed to add member');

      setAddEmail('');
      setAddRole('MEMBER');
      setShowAddMember(false);
      await fetchHousehold();
    } catch (err) {
      setAddError(err instanceof Error ? err.message : 'Failed to add member');
    } finally {
      setIsAdding(false);
    }
  };

  const handleRemoveMember = async (accountId: string, displayName: string) => {
    if (!token || !confirm(t('householdDetail.confirmRemoveMember', { name: displayName }))) return;

    try {
      const response = await apiFetch(`/households/${householdId}/members/${accountId}`, {
        method: 'DELETE',
      });

      if (!response.ok) throw new Error('Failed to remove member');

      await fetchHousehold();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to remove member');
    }
  };

  const handleRoleChange = async (accountId: string, newRole: string) => {
    if (!token) return;

    try {
      const response = await apiFetch(`/households/${householdId}/members/${accountId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ role: newRole }),
      });

      if (!response.ok) throw new Error('Failed to update role');

      await fetchHousehold();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update role');
    }
  };

  const isOwner = household?.members.some(
    (m) => m.accountId === currentUser?.id && m.role === 'OWNER'
  );

  if (isLoading) {
    return (
      <div className="flex justify-center py-8">
        <LoadingSpinner />
      </div>
    );
  }

  if (error || !household) {
    return (
      <ErrorAlert message={error || t('householdDetail.householdNotFound')}>
        <Link to="/households" className="mt-2 text-sm text-red-600 underline" onClick={onBack}>
          {t('common.goBack')}
        </Link>
      </ErrorAlert>
    );
  }

  return (
    <div>
      <div className="mb-6">
        <Link
          to="/households"
          onClick={onBack}
          className="flex items-center text-sm text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
        >
          <span className="mr-1">&larr;</span> {t('householdDetail.backToHouseholds')}
        </Link>
      </div>

      <div className="mb-6 flex items-center justify-between">
        <h2 className="text-2xl font-bold text-gray-900 dark:text-white">{household.name}</h2>
        <div className="flex items-center gap-2">
          {isOwner && (
            <>
              <button
                onClick={() => setShowAddMember(!showAddMember)}
                className="rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white hover:bg-indigo-500"
              >
                {t('householdDetail.manageMembers')}
              </button>
              <button
                onClick={handleDelete}
                className="rounded-md bg-red-600 px-3 py-2 text-sm font-semibold text-white hover:bg-red-500"
              >
                {t('householdDetail.deleteHousehold')}
              </button>
            </>
          )}
        </div>
      </div>

      {showAddMember && (
        <div className="mb-6 rounded-lg bg-white p-4 shadow dark:bg-gray-800">
          <h4 className="mb-3 text-sm font-semibold text-gray-900 dark:text-white">
            {t('householdDetail.addMember')}
          </h4>
          <form onSubmit={handleAddMember} className="flex items-end gap-3">
            <div className="flex-1">
              <label
                htmlFor="member-email"
                className="block text-sm font-medium text-gray-700 dark:text-gray-300"
              >
                {t('householdDetail.emailLabel')}
              </label>
              <input
                id="member-email"
                type="email"
                value={addEmail}
                onChange={(e) => setAddEmail(e.target.value)}
                placeholder={t('householdDetail.emailPlaceholder')}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
                required
              />
            </div>
            <div>
              <label
                htmlFor="member-role"
                className="block text-sm font-medium text-gray-700 dark:text-gray-300"
              >
                {t('householdDetail.roleLabel')}
              </label>
              <select
                id="member-role"
                value={addRole}
                onChange={(e) => setAddRole(e.target.value)}
                className="mt-1 block rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
              >
                <option value="MEMBER">{t('householdDetail.roleMember')}</option>
                <option value="OWNER">{t('householdDetail.roleOwner')}</option>
              </select>
            </div>
            <button
              type="submit"
              disabled={isAdding || !addEmail.trim()}
              className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500 disabled:opacity-50"
            >
              {isAdding ? t('common.adding') : t('common.add')}
            </button>
            <button
              type="button"
              onClick={() => {
                setShowAddMember(false);
                setAddError(null);
                setAddEmail('');
              }}
              className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
            >
              {t('common.cancel')}
            </button>
          </form>
          {addError && <p className="mt-2 text-sm text-red-600">{addError}</p>}
        </div>
      )}

      <div className="rounded-lg bg-white p-6 shadow dark:bg-gray-800">
        <h3 className="mb-4 text-lg font-semibold text-gray-900 dark:text-white">
          {t('householdDetail.members', { count: household.members.length })}
        </h3>
        <ul className="divide-y divide-gray-200 dark:divide-gray-700">
          {household.members.map((member) => (
            <li key={member.accountId} className="flex items-center justify-between py-4">
              <div className="flex items-center">
                <div className="flex h-10 w-10 items-center justify-center rounded-full bg-gray-200 text-sm font-medium text-gray-600 dark:bg-gray-600 dark:text-gray-200">
                  {member.displayName.charAt(0).toUpperCase()}
                </div>
                <div className="ml-3">
                  <p className="text-sm font-medium text-gray-900 dark:text-white">
                    {member.displayName}
                  </p>
                  <p className="text-sm text-gray-500 dark:text-gray-400">{member.email}</p>
                </div>
              </div>
              <div className="flex items-center gap-2">
                {isOwner ? (
                  <>
                    <select
                      value={member.role}
                      onChange={(e) => handleRoleChange(member.accountId, e.target.value)}
                      className="rounded-md border border-gray-300 px-2 py-1 text-xs focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
                    >
                      <option value="MEMBER">{t('householdDetail.roleMember')}</option>
                      <option value="OWNER">{t('householdDetail.roleOwner')}</option>
                    </select>
                    {member.accountId !== currentUser?.id && (
                      <button
                        onClick={() => handleRemoveMember(member.accountId, member.displayName)}
                        className="rounded p-1 text-gray-400 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-900/20"
                        title={t('householdDetail.removeMember')}
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
                    )}
                  </>
                ) : (
                  <Badge variant={member.role === 'OWNER' ? 'primary' : 'default'}>
                    {roleLabel(member.role)}
                  </Badge>
                )}
              </div>
            </li>
          ))}
        </ul>
      </div>

      <RecurringItemsList householdId={householdId} />

      <div className="mt-6">
        <CommentFeed targetType="HOUSEHOLD" targetId={householdId} />
      </div>
    </div>
  );
}
