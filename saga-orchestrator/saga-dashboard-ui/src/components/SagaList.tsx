import React from 'react';
import { Link } from 'react-router-dom';
import { SagaListItem } from '../types/saga';
import { StatusBadge } from './StatusBadge';
import { ChevronRight } from 'lucide-react';

interface Props {
  sagas: SagaListItem[];
  loading: boolean;
}

export function SagaList({ sagas, loading }: Props) {
  if (loading) {
    return <div className="text-center py-8 text-gray-500">Loading sagas...</div>;
  }

  if (sagas.length === 0) {
    return (
      <div className="text-center py-12 text-gray-400">
        <p className="text-lg">No sagas found</p>
        <p className="text-sm mt-1">Start a saga to see it here</p>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
      <table className="min-w-full divide-y divide-gray-200">
        <thead className="bg-gray-50">
          <tr>
            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Saga ID</th>
            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Type</th>
            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Current Step</th>
            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Created</th>
            <th className="px-6 py-3"></th>
          </tr>
        </thead>
        <tbody className="bg-white divide-y divide-gray-200">
          {sagas.map((saga) => (
            <tr key={saga.sagaId} className="hover:bg-gray-50 transition-colors">
              <td className="px-6 py-4 whitespace-nowrap">
                <code className="text-sm text-gray-600">{saga.sagaId.substring(0, 8)}...</code>
              </td>
              <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">{saga.sagaType}</td>
              <td className="px-6 py-4 whitespace-nowrap"><StatusBadge status={saga.status} /></td>
              <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{saga.currentStep || '-'}</td>
              <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                {new Date(saga.createdAt).toLocaleString()}
              </td>
              <td className="px-6 py-4 whitespace-nowrap text-right">
                <Link to={`/sagas/${saga.sagaId}`} className="text-indigo-600 hover:text-indigo-900">
                  <ChevronRight className="w-5 h-5" />
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
