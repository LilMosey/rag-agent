import axios from 'axios';

export const client = axios.create({
  baseURL: '/api',
  timeout: 30000
});

export function unwrap<T>(response: { data: { success: boolean; data: T; message?: string } }): T {
  if (!response.data.success) {
    throw new Error(response.data.message || '请求失败');
  }
  return response.data.data;
}
