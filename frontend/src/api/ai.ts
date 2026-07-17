import request from './request';

/** Admin AI chat — ADMIN only. Supports optional temperature and style params. */
export const chat = (data: {
  sessionId: string;
  message: string;
  userType?: string;
  temperature?: number;
  style?: string;
}) => request.post('/ai/chat', data);

/** Get anomalies list */
export const getAnomalies = (params: Record<string, unknown>) =>
  request.get('/ai/anomalies', { params });

/** Mark anomaly as resolved */
export const resolveAnomaly = (id: number) =>
  request.put(`/ai/anomalies/${id}/resolve`);

/** Trigger anomaly scan */
export const triggerAnomalyScan = () =>
  request.post('/ai/anomalies/scan');

/** Get delivery distance matrix */
export const getMatrix = () =>
  request.get('/ai/matrix');

/** Refresh delivery distance matrix */
export const refreshMatrix = () =>
  request.post('/ai/matrix/refresh');
