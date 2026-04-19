import React from 'react';
import { RefreshCw } from 'lucide-react';

interface Props {
  status: string;
  onStatusChange: (status: string) => void;
  onRefresh: () => void;
}

const statuses = ['', 'STARTED', 'EXECUTING', 'COMPENSATING', 'COMPLETED', 'COMPENSATED', 'FAILED'];

export function SagaFilters({ status, onStatusChange, onRefresh }: Props) {
  return (
    <div className="flex items-center gap-4 mb-4">
      <div className="flex items-center gap-2">
        <label className="text-sm font-medium text-gray-700">Status:</label>
        <select
          value={status}
          onChange={(e) => onStatusChange(e.target.value)}
          className="block rounded-md border-gray-300 shadow-sm text-sm focus:border-indigo-500 focus:ring-indigo-500 px-3 py-1.5 border"
        >
          <option value="">All</option>
          {statuses.filter(Boolean).map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>
      </div>
      <button
        onClick={onRefresh}
        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50 transition-colors"
      >
        <RefreshCw className="w-4 h-4" />
        Refresh
      </button>
    </div>
  );
}
