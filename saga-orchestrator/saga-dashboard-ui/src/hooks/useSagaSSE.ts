import { useEffect, useRef, useState, useCallback } from 'react';
import { SseEvent } from '../types/saga';

export function useSagaSSE(onEvent: (event: SseEvent) => void) {
  const [connected, setConnected] = useState(false);
  const eventSourceRef = useRef<EventSource | null>(null);
  const onEventRef = useRef(onEvent);
  onEventRef.current = onEvent;

  const connect = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }

    const es = new EventSource('/api/v1/sagas/stream');

    es.onopen = () => setConnected(true);

    es.onmessage = (event) => {
      try {
        const data: SseEvent = JSON.parse(event.data);
        onEventRef.current(data);
      } catch (e) {
        console.error('Failed to parse SSE event', e);
      }
    };

    es.onerror = () => {
      setConnected(false);
      es.close();
      setTimeout(connect, 3000);
    };

    eventSourceRef.current = es;
  }, []);

  useEffect(() => {
    connect();
    return () => {
      eventSourceRef.current?.close();
    };
  }, [connect]);

  return { connected };
}
