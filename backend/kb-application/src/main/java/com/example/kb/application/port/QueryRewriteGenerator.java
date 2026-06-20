package com.example.kb.application.port;

import com.example.kb.domain.model.ConversationMessage;
import com.example.kb.domain.model.QueryIntent;

import java.util.List;

public interface QueryRewriteGenerator {

    RewriteResult rewrite(RewriteCommand command);

    record RewriteCommand(
            String userQuestion,
            QueryIntent queryIntent,
            List<ConversationMessage> recentMessages
    ) {
    }

    record RewriteResult(
            String rewrittenQuery,
            Boolean changed,
            String reason,
            String provider,
            String model
    ) {
    }
}
