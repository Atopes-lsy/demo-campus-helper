import request from './request';

export const getOrders = (params: Record<string, unknown>) =>
  request.get('/orders', { params });

export const getOrder = (id: number) =>
  request.get(`/orders/${id}`);

export const createOrder = (data: Record<string, unknown>) =>
  request.post('/orders', data);

export const updateOrderStatus = (id: number, data: Record<string, unknown>) =>
  request.put(`/orders/${id}/status`, data);

export const getOrderTracking = (id: number) =>
  request.get(`/orders/${id}/tracking`);
