import axios, { AxiosError } from 'axios';

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

export function toApiError(error: unknown): Error {
  if (error instanceof AxiosError) {
    const responseData = error.response?.data as { message?: string } | undefined;
    return new Error(responseData?.message || error.message);
  }
  if (error instanceof Error) {
    return error;
  }
  return new Error('请求失败');
}
