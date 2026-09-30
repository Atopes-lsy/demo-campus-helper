import axios from 'axios';
import { message } from 'antd';

const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
});

http.interceptors.request.use((config) => {
  try {
    const auth = JSON.parse(localStorage.getItem('campus_helper_auth') || '{}');
    if (auth.token) config.headers.Authorization = `Bearer ${auth.token}`;
  } catch {
    localStorage.removeItem('campus_helper_auth');
  }
  return config;
});

http.interceptors.response.use(
  (response) => {
    const res = response.data;
    if (res.code !== 200) {
      message.error(res.message || '请求失败');
      return Promise.reject(new Error(res.message));
    }
    return res;
  },
  (error) => {
    const status = error.response?.status;
    const errorMessage = error.response?.data?.message || error.message || '网络错误';
    if (status === 401) {
      localStorage.removeItem('campus_helper_auth');
      if (window.location.pathname !== '/login') window.location.assign('/login');
    }
    message.error(errorMessage);
    return Promise.reject(error);
  }
);

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const request: any = http;

export default request;
