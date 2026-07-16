import request from './request';

export const getStudents = (params: Record<string, unknown>) =>
  request.get('/students', { params });

export const updateStudent = (id: number, data: Record<string, unknown>) =>
  request.put(`/students/${id}`, data);
