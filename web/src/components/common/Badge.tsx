interface BadgeProps {
  children: string;
  variant?: 'default' | 'primary' | 'success' | 'warning';
}

const variantClasses = {
  default: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-200',
  primary: 'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300',
  success: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
  warning: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300',
};

export default function Badge({ children, variant = 'default' }: BadgeProps) {
  return (
    <span className={`rounded-full px-2 py-1 text-xs font-medium ${variantClasses[variant]}`}>
      {children}
    </span>
  );
}
