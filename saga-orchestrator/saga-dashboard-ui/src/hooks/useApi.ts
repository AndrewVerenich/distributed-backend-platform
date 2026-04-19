import { useState, useEffect, useCallback } from 'react';
import { SagaListItem, SagaDetail, SagaStats } from '../types/saga';

const API_BASE = '/api/v1/sagas';

export function useSagaList(status?: string, sagaType?: string) {
  const [sagas, setSagas] = useState<SagaListItem[]>([]);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    setLoading(true);
    const params = new URLSearchParams();
    if (status) params.set('status', status);
    if (sagaType) params.set('sagaType', sagaType);

    try {
      const res = await fetch(`${API_BASE}?${params.toString()}`);
      const data = await res.json();
      setSagas(data);
    } catch (e) {
      console.error('Failed to fetch sagas', e);
    } finally {
      setLoading(false);
    }
  }, [status, sagaType]);

  useEffect(() => { refresh(); }, [refresh]);

  return { sagas, loading, refresh };
}

export function useSagaDetail(sagaId: string | undefined) {
  const [saga, setSaga] = useState<SagaDetail | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    if (!sagaId) return;
    setLoading(true);
    try {
      const res = await fetch(`${API_BASE}/${sagaId}`);
      const data = await res.json();
      setSaga(data);
    } catch (e) {
      console.error('Failed to fetch saga detail', e);
    } finally {
      setLoading(false);
    }
  }, [sagaId]);

  useEffect(() => { refresh(); }, [refresh]);

  return { saga, loading, refresh };
}

export function useSagaStats() {
  const [stats, setStats] = useState<SagaStats>({ total: 0, active: 0, completed: 0, compensated: 0, failed: 0 });

  const refresh = useCallback(async () => {
    try {
      const res = await fetch(`${API_BASE}/stats`);
      const data = await res.json();
      setStats(data);
    } catch (e) {
      console.error('Failed to fetch stats', e);
    }
  }, []);

  useEffect(() => { refresh(); }, [refresh]);

  return { stats, refresh };
}
