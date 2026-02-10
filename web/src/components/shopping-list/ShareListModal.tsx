import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Modal } from '../common';
import { UserShareTab, LinkShareTab } from './share';

interface ShareListModalProps {
  listId: string;
  onClose: () => void;
}

type Tab = 'user' | 'link';

export default function ShareListModal({ listId, onClose }: ShareListModalProps) {
  const { t } = useTranslation();
  const [tab, setTab] = useState<Tab>('user');

  return (
    <Modal isOpen={true} onClose={onClose} title={t('shareList.title')}>
      {/* Tabs */}
      <div className="flex border-b border-gray-200 mb-4 -mt-2">
        <button
          type="button"
          onClick={() => setTab('user')}
          className={`px-4 py-2 text-sm font-medium ${
            tab === 'user'
              ? 'border-b-2 border-indigo-500 text-indigo-600'
              : 'text-gray-500 hover:text-gray-700'
          }`}
        >
          {t('shareList.shareWithUser')}
        </button>
        <button
          type="button"
          onClick={() => setTab('link')}
          className={`px-4 py-2 text-sm font-medium ${
            tab === 'link'
              ? 'border-b-2 border-indigo-500 text-indigo-600'
              : 'text-gray-500 hover:text-gray-700'
          }`}
        >
          {t('shareList.shareLink')}
        </button>
      </div>

      {tab === 'user' && <UserShareTab listId={listId} onClose={onClose} />}
      {tab === 'link' && <LinkShareTab listId={listId} onClose={onClose} />}
    </Modal>
  );
}
