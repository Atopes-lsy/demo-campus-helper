import request from './request';

export const studentLogin = (data: Record<string, unknown>) =>
  request.post('/auth/student/login', data);

export const studentRegister = (data: Record<string, unknown>) =>
  request.post('/auth/student/register', data);

export const runnerLogin = (data: Record<string, unknown>) =>
  request.post('/auth/runner/login', data);

export const adminLogin = (data: Record<string, unknown>) =>
  request.post('/auth/admin/login', data);
