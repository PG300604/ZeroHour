import { api } from './api';

export function subscribeToAgentStream(sessionId, onMessage, onError) {
  const url = api.getStreamUrl(sessionId);
  let eventSource = new EventSource(url, { withCredentials: true });

  eventSource.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      onMessage(data);
    } catch (e) {
      console.error('Failed to parse SSE data', e);
    }
  };

  eventSource.onerror = (err) => {
    console.error('SSE connection error:', err);
    if (onError) onError(err);
    eventSource.close();
  };

  return {
    close: () => {
      if (eventSource) {
        eventSource.close();
      }
    }
  };
}
