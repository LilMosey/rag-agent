package com.example.kb.application.port;

import com.example.kb.domain.model.ConversationRetrievalTask;

import java.util.List;

public interface ConversationRetrievalTaskRepository {

    ConversationRetrievalTask save(ConversationRetrievalTask task);

    void saveBatch(List<ConversationRetrievalTask> tasks);
}
