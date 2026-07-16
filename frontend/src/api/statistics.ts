import request from './request';

export const getOverview = () =>
  request.get('/statistics/overview');

export const getTypeDistribution = () =>
  request.get('/statistics/type-distribution');
