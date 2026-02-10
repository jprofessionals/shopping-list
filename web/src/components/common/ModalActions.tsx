import type { ReactNode } from 'react';

interface ModalActionsProps {
  children: ReactNode;
}

export default function ModalActions({ children }: ModalActionsProps) {
  return (
    <div className="mt-5 sm:mt-6 sm:grid sm:grid-flow-row-dense sm:grid-cols-2 sm:gap-3">
      {children}
    </div>
  );
}

interface PrimaryButtonProps {
  type?: 'button' | 'submit';
  disabled?: boolean;
  onClick?: () => void;
  children: ReactNode;
}

export function PrimaryButton({
  type = 'button',
  disabled = false,
  onClick,
  children,
}: PrimaryButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled}
      onClick={onClick}
      className="inline-flex w-full justify-center rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:opacity-50 sm:col-start-2"
    >
      {children}
    </button>
  );
}

interface SecondaryButtonProps {
  onClick: () => void;
  children: ReactNode;
}

export function SecondaryButton({ onClick, children }: SecondaryButtonProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="mt-3 inline-flex w-full justify-center rounded-md bg-white px-3 py-2 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 sm:col-start-1 sm:mt-0"
    >
      {children}
    </button>
  );
}
