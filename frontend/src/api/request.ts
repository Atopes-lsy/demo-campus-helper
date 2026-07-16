import axios from 'axios';
import { message } from 'antd';

const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
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
    message.error(error.message || '网络错误');
    return Promise.reject(error);
  }
);

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const request: any = http;

export default request;
