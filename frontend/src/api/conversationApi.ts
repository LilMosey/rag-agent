import { client, unwrap } from './client';
import type {
  AnswerDeltaEvent,
  AnswerDoneEvent,
  Conversation,
  ConversationMessage,
  RetrievalDoneEvent,
  RetrievalReference,
  RouterResult,
  SendMessageResponse,
  StreamErrorEvent
} from '../types/domain';

export async function createConversation(): Promise<Conversation> {
  const response = await client.post('/conversations');
  return unwrap<Conversation>(response);
}

export async function listConversations(): Promise<Conversation[]> {
  const response = await client.get('/conversations');
  return unwrap<Conversation[]>(response);
}

export async function updateConversationTitle(conversationId: number, title: string): Promise<Conversation> {
  const response = await client.put(`/conversations/${conversationId}`, { title });
  return unwrap<Conversation>(response);
}

export async function deleteConversation(conversationId: number): Promise<void> {
  const response = await client.delete(`/conversations/${conversationId}`);
  unwrap<void>(response);
}

export async function listConversationMessages(conversationId: number): Promise<ConversationMessage[]> {
  const response = await client.get(`/conversations/${conversationId}/messages`);
  return unwrap<ConversationMessage[]>(response);
}

export async function sendConversationMessage(conversationId: number, content: string): Promise<SendMessageResponse> {
  const response = await client.post(`/conversations/${conversationId}/messages`, { content });
  return unwrap<SendMessageResponse>(response);
}

export interface ConversationMessageStreamHandlers {
  onRouter?: (router: RouterResult) => void;
  onRetrievalDone?: (event: RetrievalDoneEvent) => void;
  onAnswerDelta?: (event: AnswerDeltaEvent) => void;
  onAnswerDone?: (event: AnswerDoneEvent) => void;
  onReferences?: (references: RetrievalReference[]) => void;
  onError?: (event: StreamErrorEvent) => void;
}

export async function sendConversationMessageStream(
  conversationId: number,
  content: string,
  handlers: ConversationMessageStreamHandlers
): Promise<void> {
  const response = await fetch(`/api/conversations/${conversationId}/messages/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ content })
  });
  if (!response.ok || !response.body) {
    throw new Error(`流式请求失败: ${response.status}`);
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  while (true) {
    const result = await reader.read();
    if (result.done) {
      break;
    }
    buffer += decoder.decode(result.value, { stream: true });
    const blocks = buffer.split(/\r?\n\r?\n/);
    buffer = blocks.pop() || '';
    for (const block of blocks) {
      dispatchSseBlock(block, handlers);
    }
  }
  if (buffer.trim()) {
    dispatchSseBlock(buffer, handlers);
  }
}

function dispatchSseBlock(block: string, handlers: ConversationMessageStreamHandlers) {
  const lines = block.split(/\r?\n/);
  let eventName = '';
  const dataLines: string[] = [];
  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventName = line.substring('event:'.length).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.substring('data:'.length).trim());
    }
  }
  if (!eventName || dataLines.length === 0) {
    return;
  }
  const dataText = dataLines.join('\n');
  const data = JSON.parse(dataText) as unknown;
  if (eventName === 'router') {
    handlers.onRouter?.(data as RouterResult);
  } else if (eventName === 'retrieval_done') {
    handlers.onRetrievalDone?.(data as RetrievalDoneEvent);
  } else if (eventName === 'answer_delta') {
    handlers.onAnswerDelta?.(data as AnswerDeltaEvent);
  } else if (eventName === 'answer_done') {
    handlers.onAnswerDone?.(data as AnswerDoneEvent);
  } else if (eventName === 'references') {
    handlers.onReferences?.(data as RetrievalReference[]);
  } else if (eventName === 'error') {
    handlers.onError?.(data as StreamErrorEvent);
  }
}
