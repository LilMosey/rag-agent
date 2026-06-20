package com.example.kb.infrastructure.index;

public interface IndexBuildStep {

    String name();

    void execute(IndexBuildContext context);
}
