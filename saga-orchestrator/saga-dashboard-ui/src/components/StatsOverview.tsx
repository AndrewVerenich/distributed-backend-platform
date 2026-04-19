import React from 'react';
import { SagaStats } from '../types/saga';
import { Activity, CheckCircle, XCircle, RotateCcw, BarChart3 } from 'lucide-react';

interface Props {
  stats: SagaStats;
}

export function StatsOverview({ stats }: Props) {
  const cards = [
    { label: 'Total Sagas', value: stats.total, icon: BarChart3, color: 'text-gray-700' },
    { label: 'Active', value: stats.active, icon: Activity, color: 'text-yellow-600' },
    { label: 'Completed', value: stats.completed, icon: CheckCircle, color: 'text-green-600' },
    { label: 'Compensated', value: stats.compensated, icon: RotateCcw, color: 'text-purple-600' },
    { label: 'Failed', value: stats.failed, icon: XCircle, color: 'text-red-600' },
  ];

  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-4 mb-6">
      {cards.map((card) => (
        <div key={card.label} className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-gray-500">{card.label}</p>
              <p className={`text-2xl font-bold ${card.color}`}>{card.value}</p>
            </div>
            <card.icon className={`w-8 h-8 ${card.color} opacity-50`} />
          </div>
        </div>
      ))}
    </div>
  );
}
