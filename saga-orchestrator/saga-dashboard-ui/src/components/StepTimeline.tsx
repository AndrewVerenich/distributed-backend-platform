import React from 'react';
import { StepDetail } from '../types/saga';
import { StatusBadge, StepTypeBadge } from './StatusBadge';
import { CheckCircle, XCircle, Clock, RotateCcw, Loader, Circle } from 'lucide-react';

interface Props {
  steps: StepDetail[];
}

function StepIcon({ status }: { status: string }) {
  switch (status) {
    case 'COMPLETED': return <CheckCircle className="w-6 h-6 text-green-500" />;
    case 'FAILED': return <XCircle className="w-6 h-6 text-red-500" />;
    case 'EXECUTING': return <Loader className="w-6 h-6 text-yellow-500 animate-spin" />;
    case 'COMPENSATING': return <RotateCcw className="w-6 h-6 text-orange-500 animate-spin" />;
    case 'COMPENSATED': return <RotateCcw className="w-6 h-6 text-purple-500" />;
    case 'PENDING': return <Circle className="w-6 h-6 text-gray-300" />;
    default: return <Clock className="w-6 h-6 text-gray-400" />;
  }
}

export function StepTimeline({ steps }: Props) {
  return (
    <div className="flow-root">
      <ul className="-mb-8">
        {steps.map((step, idx) => (
          <li key={step.stepName}>
            <div className="relative pb-8">
              {idx < steps.length - 1 && (
                <span className="absolute top-6 left-3 -ml-px h-full w-0.5 bg-gray-200" />
              )}
              <div className="relative flex items-start space-x-4">
                <div className="flex-shrink-0 mt-0.5">
                  <StepIcon status={step.status} />
                </div>
                <div className="flex-1 min-w-0 bg-white rounded-lg border border-gray-200 p-4 shadow-sm">
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2">
                      <h4 className="text-sm font-semibold text-gray-900">{step.stepName}</h4>
                      <StepTypeBadge stepType={step.stepType} />
                    </div>
                    <StatusBadge status={step.status} />
                  </div>

                  <div className="grid grid-cols-2 gap-2 text-xs text-gray-500">
                    {step.startedAt && <div>Started: {new Date(step.startedAt).toLocaleTimeString()}</div>}
                    {step.completedAt && <div>Completed: {new Date(step.completedAt).toLocaleTimeString()}</div>}
                    {step.retryCount > 0 && <div className="text-orange-600">Retries: {step.retryCount}</div>}
                  </div>

                  {step.errorMessage && (
                    <div className="mt-2 p-2 bg-red-50 border border-red-200 rounded text-xs text-red-700">
                      {step.errorMessage}
                    </div>
                  )}

                  {step.replyPayload && (
                    <details className="mt-2">
                      <summary className="text-xs text-gray-400 cursor-pointer hover:text-gray-600">
                        Reply payload
                      </summary>
                      <pre className="mt-1 p-2 bg-gray-50 rounded text-xs text-gray-600 overflow-x-auto">
                        {(() => { try { return JSON.stringify(JSON.parse(step.replyPayload), null, 2); } catch { return step.replyPayload; }})()}
                      </pre>
                    </details>
                  )}
                </div>
              </div>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
