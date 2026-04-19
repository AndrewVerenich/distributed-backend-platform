export interface SagaListItem {
  sagaId: string;
  sagaType: string;
  status: string;
  currentStep: string | null;
  createdAt: string;
  completedAt: string | null;
}

export interface SagaDetail {
  sagaId: string;
  sagaType: string;
  status: string;
  currentStep: string | null;
  payload: string;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
  steps: StepDetail[];
}

export interface StepDetail {
  stepName: string;
  stepType: string;
  stepOrder: number;
  status: string;
  commandPayload: string | null;
  replyPayload: string | null;
  errorMessage: string | null;
  retryCount: number;
  startedAt: string | null;
  completedAt: string | null;
}

export interface SagaStats {
  total: number;
  active: number;
  completed: number;
  compensated: number;
  failed: number;
}

export interface SseEvent {
  type: string;
  sagaId: string;
  sagaType: string;
  status: string;
  stepName?: string;
  payload: string;
}
