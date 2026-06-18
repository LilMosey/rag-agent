import { client, unwrap } from './client';
import type { ChunkStrategy, FileStatus, KnowledgeFile } from '../types/domain';

export interface FileQuery {
  keyword?: string;
  status?: FileStatus | '';
  page?: number;
  size?: number;
}

export interface UploadFileOptions {
  chunkStrategy: ChunkStrategy;
  chunkSize: number;
  chunkOverlap: number;
}

export async function listFiles(knowledgeBaseId: number, query: FileQuery): Promise<KnowledgeFile[]> {
  const response = await client.get(`/knowledge-bases/${knowledgeBaseId}/files`, {
    params: query
  });
  return unwrap<KnowledgeFile[]>(response);
}

export async function uploadFile(
  knowledgeBaseId: number,
  file: File,
  options: UploadFileOptions
): Promise<KnowledgeFile> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('chunkStrategy', options.chunkStrategy);
  formData.append('chunkSize', String(options.chunkSize));
  formData.append('chunkOverlap', String(options.chunkOverlap));
  const response = await client.post(`/knowledge-bases/${knowledgeBaseId}/files`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });
  return unwrap<KnowledgeFile>(response);
}

export async function deleteFile(knowledgeBaseId: number, fileId: number): Promise<void> {
  const response = await client.delete(`/knowledge-bases/${knowledgeBaseId}/files/${fileId}`);
  unwrap<void>(response);
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
