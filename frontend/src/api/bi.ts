import request from './request';

/** KPI cards + week trend */
export const getBIOverview = () => request.get('/bi/overview');

/** Full dashboard: status, type, trend, hourly peak, geo heatmap */
export const getBIDashboard = (days: number) =>
  request.get('/bi/dashboard', { params: { days } });

/** Revenue analysis: trend + summary stats */
export const getBIRevenue = (period: string) =>
  request.get('/bi/revenue', { params: { period } });

/** Status & type distribution */
export const getBIDistribution = (days: number) =>
  request.get('/bi/distribution', { params: { days } });

/** Runner performance ranking */
export const getBIRunnerRanking = (topN: number = 10) =>
  request.get('/bi/runners', { params: { topN } });

/** Geo-heatmap data */
export const getBIHeatmap = (days: number) =>
  request.get('/bi/heatmap', { params: { days } });
