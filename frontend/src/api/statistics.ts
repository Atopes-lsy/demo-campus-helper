import request from './request';

export const getOverview = () =>
  request.get('/statistics/overview');

export const getTypeDistribution = () =>
  request.get('/statistics/type-distribution');

/** period: "day" | "week" | "month" */
export const getRevenueTrend = (period: string) =>
  request.get('/statistics/revenue-trend', { params: { period } });
