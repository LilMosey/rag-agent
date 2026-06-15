import { client, unwrap } from './client';
import type { KnowledgeBase, KnowledgeBasePayload } from '../types/domain';

export async function listKnowledgeBases(): Promise<KnowledgeBase[]> {
  const response = await client.get('/knowledge-bases');
  return unwrap<KnowledgeBase[]>(response);
}

export async function createKnowledgeBase(payload: KnowledgeBasePayload): Promise<KnowledgeBase> {
  const response = await client.post('/knowledge-bases', payload);
  return unwrap<KnowledgeBase>(response);
}

export async function updateKnowledgeBase(id: number, payload: KnowledgeBasePayload): Promise<KnowledgeBase> {
  const response = await client.put(`/knowledge-bases/${id}`, payload);
  return unwrap<KnowledgeBase>(response);
}

export async function deleteKnowledgeBase(id: number): Promise<void> {
  const response = await client.delete(`/knowledge-bases/${id}`);
  unwrap<void>(response);
}
