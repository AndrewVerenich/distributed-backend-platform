import React, { useState, useCallback } from 'react';
import { useSagaList, useSagaStats } from '../hooks/useApi';
import { useSagaSSE } from '../hooks/useSagaSSE';
import { StatsOverview } from '../components/StatsOverview';
import { SagaList } from '../components/SagaList';
import { SagaFilters } from '../components/SagaFilters';
import { ConnectionIndicator } from '../components/ConnectionIndicator';
import { SseEvent } from '../types/saga';

export function SagaListPage() {
  const [statusFilter, setStatusFilter] = useState('');
  const { sagas, loading, refresh } = useSagaList(statusFilter || undefined);
  const { stats, refresh: refreshStats } = useSagaStats();

  const handleSseEvent = useCallback((_event: SseEvent) => {
    refresh();
    refreshStats();
  }, [refresh, refreshStats]);

  const { connected } = useSagaSSE(handleSseEvent);

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Saga Dashboard</h1>
        <ConnectionIndicator connected={connected} />
      </div>

      <StatsOverview stats={stats} />

      <SagaFilters
        status={statusFilter}
        onStatusChange={setStatusFilter}
        onRefresh={() => { refresh(); refreshStats(); }}
      />

      <SagaList sagas={sagas} loading={loading} />
    </div>
  );
}
