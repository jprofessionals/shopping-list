import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ErrorAlert } from '../../common';

interface LinkDisplayProps {
  link: string;
  onClose: () => void;
}

export default function LinkDisplay({ link, onClose }: LinkDisplayProps) {
  const { t } = useTranslation();
  const [copySuccess, setCopySuccess] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleCopyLink = async () => {
    try {
      await navigator.clipboard.writeText(link);
      setCopySuccess(true);
      setTimeout(() => setCopySuccess(false), 2000);
    } catch {
      setError('Failed to copy link');
    }
  };

  return (
    <div className="space-y-4">
      {error && (
        <div className="mb-4">
          <ErrorAlert message={error} />
        </div>
      )}

      <div>
        <label className="block text-sm font-medium leading-6 text-gray-900 mb-2">
          {t('linkShare.shareLink')}
        </label>
        <div className="flex gap-2">
          <input
            type="text"
            readOnly
            value={link}
            className="block w-full rounded-md border-0 px-3 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 bg-gray-50 sm:text-sm sm:leading-6"
          />
          <button
            type="button"
            onClick={handleCopyLink}
            className="inline-flex items-center rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
          >
            {copySuccess ? t('linkShare.copied') : t('linkShare.copy')}
          </button>
        </div>
      </div>
      <div className="mt-5 flex justify-end">
        <button
          type="button"
          onClick={onClose}
          className="inline-flex justify-center rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
        >
          {t('common.done')}
        </button>
      </div>
    </div>
  );
}
