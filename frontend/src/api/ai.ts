import request from './request';

export const chat = (data: { sessionId: string; message: string }) =>
  request.post('/ai/chat', data);

export const getChatHistory = (sessionId: string) =>
  request.get(`/ai/chat/history/${sessionId}`);

export const getPricingSuggestion = (orderId: number) =>
  request.get(`/ai/pricing/suggest/${orderId}`);

export const getAnomalies = (params: Record<string, unknown>) =>
  request.get('/ai/anomalies', { params });

export const resolveAnomaly = (id: number) =>
  request.put(`/ai/anomalies/${id}/resolve`);
