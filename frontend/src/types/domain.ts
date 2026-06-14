export type FileStatus = 'UPLOADED' | 'PENDING_PARSE' | 'PARSING' | 'PARSE_FAILED' | 'READY' | 'DISABLED';
export type FileType = 'WORD' | 'MARKDOWN' | 'TEXT' | 'PDF_RESERVED';

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
}

export interface KnowledgeBase {
  id: number;
  name: string;
  description?: string;
  createdAt: string;
  updatedAt: string;
}

export interface KnowledgeFile {
  id: number;
  knowledgeBaseId: number;
  originalFilename: string;
  fileExt: string;
  contentType?: string;
  fileSize: number;
  checksumSha256: string;
  storageBucket: string;
  storageObjectKey: string;
  fileType: FileType;
  fileStatus: FileStatus;
  parseError?: string;
  createdAt: string;
  updatedAt: string;
}

export interface KnowledgeBasePayload {
  name: string;
  description?: string;
}
