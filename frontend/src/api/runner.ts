import request from './request';

export const getRunners = (params: Record<string, unknown>) =>
  request.get('/runners', { params });

export const getRunner = (id: number) =>
  request.get(`/runners/${id}`);

export const getTopRunners = (limit = 5) =>
  request.get('/runners/top', { params: { limit } });

export const createRunner = (data: Record<string, unknown>) =>
  request.post('/runners', data);

export const updateRunner = (id: number, data: Record<string, unknown>) =>
  request.put(`/runners/${id}`, data);

export const getRunnerEarnings = (id: number) =>
  request.get(`/runners/${id}/earnings`);
