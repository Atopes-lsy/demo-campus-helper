import request from './request';

export const submitReview = (data: Record<string, unknown>) =>
  request.post('/reviews', data);

export const getOrderReview = (orderId: number, reviewerType?: number) =>
  request.get(`/reviews/order/${orderId}`, { params: { reviewerType } });
