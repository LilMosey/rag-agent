import { client, unwrap } from './client';
import type { FileStatus, KnowledgeFile } from '../types/domain';

export interface FileQuery {
  keyword?: string;
  status?: FileStatus | '';
  page?: number;
  size?: number;
}

export async function listFiles(knowledgeBaseId: number, query: FileQuery): Promise<KnowledgeFile[]> {
  const response = await client.get(`/knowledge-bases/${knowledgeBaseId}/files`, {
    params: query
  });
  return unwrap<KnowledgeFile[]>(response);
}

export async function uploadFile(knowledgeBaseId: number, file: File): Promise<KnowledgeFile> {
  const formData = new FormData();
  formData.append('file', file);
  const response = await client.post(`/knowledge-bases/${knowledgeBaseId}/files`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });
  return unwrap<KnowledgeFile>(response);
}

export async function deleteFile(knowledgeBaseId: number, fileId: number): Promise<void> {
  await client.delete(`/knowledge-bases/${knowledgeBaseId}/files/${fileId}`);
}

export async function downloadFile(knowledgeBaseId: number, file: KnowledgeFile): Promise<void> {
  const response = await client.get(`/knowledge-bases/${knowledgeBaseId}/files/${file.id}/download`, {
    responseType: 'blob'
  });
  const url = URL.createObjectURL(response.data);
  const link = document.createElement('a');
  link.href = url;
  link.download = file.originalFilename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
