import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';

export function useWebSocket(userId: string, userType: string) {
  const [connected, setConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    const client = new Client({
      brokerURL: 'ws://localhost:8080/ws',
      connectHeaders: {},
      onConnect: () => setConnected(true),
      onDisconnect: () => setConnected(false),
    });
    client.activate();
    clientRef.current = client;
    return () => { client.deactivate(); };
  }, []);

  return { connected, client: clientRef.current };
}
