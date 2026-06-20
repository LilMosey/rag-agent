package com.example.kb.application.port;

public interface HydeGenerator {

    HydeResult generate(HydeCommand command);

    record HydeCommand(
            String userQuestion
    ) {
    }

    record HydeResult(
            String hypotheticalAnswer,
            String provider,
            String model
    ) {
    }
}
