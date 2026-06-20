package com.example.kb.application.port;

import java.util.List;

public interface MultiQueryGenerator {

    MultiQueryResult generate(MultiQueryCommand command);

    record MultiQueryCommand(
            String userQuestion,
            Integer queryCount
    ) {
    }

    record MultiQueryResult(
            List<String> queries,
            String provider,
            String model
    ) {
    }
}
