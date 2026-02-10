import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { HouseholdList, CreateHouseholdModal } from '../household';

export default function HouseholdsPage() {
  const [showCreateModal, setShowCreateModal] = useState(false);
  const navigate = useNavigate();

  const handleSelectHousehold = (householdId: string) => {
    navigate(`/households/${householdId}`);
  };

  return (
    <>
      <HouseholdList
        onCreateClick={() => setShowCreateModal(true)}
        onSelectHousehold={handleSelectHousehold}
      />
      <CreateHouseholdModal isOpen={showCreateModal} onClose={() => setShowCreateModal(false)} />
    </>
  );
}
