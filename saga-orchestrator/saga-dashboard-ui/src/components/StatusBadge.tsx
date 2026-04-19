import React from 'react';

const statusColors: Record<string, string> = {
  STARTED: 'bg-blue-100 text-blue-800',
  EXECUTING: 'bg-yellow-100 text-yellow-800',
  COMPENSATING: 'bg-orange-100 text-orange-800',
  COMPLETED: 'bg-green-100 text-green-800',
  COMPENSATED: 'bg-purple-100 text-purple-800',
  FAILED: 'bg-red-100 text-red-800',
  PENDING: 'bg-gray-100 text-gray-800',
  SKIPPED: 'bg-gray-100 text-gray-500',
};

export function StatusBadge({ status }: { status: string }) {
  const color = statusColors[status] || 'bg-gray-100 text-gray-800';
  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${color}`}>
      {status}
    </span>
  );
}

const stepTypeColors: Record<string, string> = {
  COMPENSABLE: 'bg-blue-50 text-blue-700 border-blue-200',
  PIVOT: 'bg-amber-50 text-amber-700 border-amber-200',
  RETRYABLE: 'bg-emerald-50 text-emerald-700 border-emerald-200',
};

export function StepTypeBadge({ stepType }: { stepType: string }) {
  const color = stepTypeColors[stepType] || 'bg-gray-50 text-gray-700 border-gray-200';
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded border text-xs font-medium ${color}`}>
      {stepType}
    </span>
  );
}
