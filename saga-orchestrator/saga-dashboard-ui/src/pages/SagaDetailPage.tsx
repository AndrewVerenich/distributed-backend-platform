import React, { useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useSagaDetail } from '../hooks/useApi';
import { useSagaSSE } from '../hooks/useSagaSSE';
import { StatusBadge } from '../components/StatusBadge';
import { StepTimeline } from '../components/StepTimeline';
import { ConnectionIndicator } from '../components/ConnectionIndicator';
import { ArrowLeft } from 'lucide-react';
import { SseEvent } from '../types/saga';

export function SagaDetailPage() {
  const { sagaId } = useParams<{ sagaId: string }>();
  const { saga, loading, refresh } = useSagaDetail(sagaId);

  const handleSseEvent = useCallback((event: SseEvent) => {
    if (event.sagaId === sagaId) {
      refresh();
    }
  }, [sagaId, refresh]);

  const { connected } = useSagaSSE(handleSseEvent);

  if (loading) {
    return <div className="text-center py-12 text-gray-500">Loading saga details...</div>;
  }

  if (!saga) {
    return <div className="text-center py-12 text-red-500">Saga not found</div>;
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-4">
          <Link to="/" className="text-gray-400 hover:text-gray-600 transition-colors">
            <ArrowLeft className="w-6 h-6" />
          </Link>
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Saga Detail</h1>
            <p className="text-sm text-gray-500">{saga.sagaId}</p>
          </div>
        </div>
        <ConnectionIndicator connected={connected} />
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-6">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div>
            <label className="text-xs font-medium text-gray-500 uppercase">Type</label>
            <p className="text-sm font-semibold text-gray-900 mt-1">{saga.sagaType}</p>
          </div>
          <div>
            <label className="text-xs font-medium text-gray-500 uppercase">Status</label>
            <div className="mt-1"><StatusBadge status={saga.status} /></div>
          </div>
          <div>
            <label className="text-xs font-medium text-gray-500 uppercase">Created</label>
            <p className="text-sm text-gray-700 mt-1">{new Date(saga.createdAt).toLocaleString()}</p>
          </div>
          <div>
            <label className="text-xs font-medium text-gray-500 uppercase">Completed</label>
            <p className="text-sm text-gray-700 mt-1">
              {saga.completedAt ? new Date(saga.completedAt).toLocaleString() : '-'}
            </p>
          </div>
        </div>

        <details className="mt-4">
          <summary className="text-xs text-gray-400 cursor-pointer hover:text-gray-600">
            Saga payload
          </summary>
          <pre className="mt-2 p-3 bg-gray-50 rounded text-xs text-gray-600 overflow-x-auto">
            {(() => { try { return JSON.stringify(JSON.parse(saga.payload), null, 2); } catch { return saga.payload; }})()}
          </pre>
        </details>
      </div>

      <h2 className="text-lg font-semibold text-gray-900 mb-4">Steps</h2>
      <StepTimeline steps={saga.steps} />
    </div>
  );
}
