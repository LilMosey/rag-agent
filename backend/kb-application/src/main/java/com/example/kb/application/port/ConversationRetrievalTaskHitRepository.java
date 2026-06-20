package com.example.kb.application.port;

import com.example.kb.domain.model.ConversationRetrievalTaskHit;

import java.util.List;

public interface ConversationRetrievalTaskHitRepository {

    void saveBatch(List<ConversationRetrievalTaskHit> hits);
}
