import request from './request';

export const getRunners = (params: Record<string, unknown>) =>
  request.get('/runners', { params });

export const getRunner = (id: number) =>
  request.get(`/runners/${id}`);

export const getTopRunners = (limit = 5) =>
  request.get('/runners/top', { params: { limit } });
