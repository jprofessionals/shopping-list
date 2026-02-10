import { useParams, useNavigate } from 'react-router-dom';
import { HouseholdDetail } from '../household';
import { ErrorAlert } from '../common';

export default function HouseholdDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const handleBack = () => {
    navigate('/households');
  };

  if (!id) {
    return (
      <ErrorAlert message="Invalid household ID">
        <button onClick={handleBack} className="mt-2 text-sm text-red-600 underline">
          Go back to households
        </button>
      </ErrorAlert>
    );
  }

  return <HouseholdDetail householdId={id} onBack={handleBack} />;
}
