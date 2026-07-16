import request from './request';

export const getPayments = (params: Record<string, unknown>) =>
  request.get('/payments', { params });

export const getPayment = (paymentNo: string) =>
  request.get(`/payments/${paymentNo}`);

export const createPayment = (data: Record<string, unknown>) =>
  request.post('/payments', data);
