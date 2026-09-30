import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';

export interface RealtimeNotification {
  title?: string;
  content?: string;
  relatedOrderId?: number;
}

export function useWebSocket(enabled: boolean, onNotification: (payload: RealtimeNotification) => void) {
  const [connected, setConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);
  const callbackRef = useRef(onNotification);
  callbackRef.current = onNotification;

  useEffect(() => {
    if (!enabled) return;
    let token = '';
    try {
      token = JSON.parse(localStorage.getItem('campus_helper_auth') || '{}').token || '';
    } catch { /* handled by HTTP authentication */ }
    if (!token) return;

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const client = new Client({
      brokerURL: `${protocol}//${window.location.host}/ws`,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true);
        client.subscribe('/user/queue/notifications', (message) => {
          try { callbackRef.current(JSON.parse(message.body)); } catch { /* ignore malformed message */ }
        });
      },
      onDisconnect: () => setConnected(false),
      onStompError: () => setConnected(false),
    });
    client.activate();
    clientRef.current = client;
    return () => { client.deactivate(); };
  }, [enabled]);

  return { connected, client: clientRef.current };
}
