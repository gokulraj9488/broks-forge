package com.broksforge.modules.benchmark.domain;

/**
 * The fixed catalogue of built-in Benchmark Gallery templates (see
 * {@code BenchmarkGalleryCatalog}). Each provisions a starter dataset, prompt and
 * evaluation profile so a new project has something runnable in one click instead of
 * starting blank.
 */
public enum BenchmarkTemplateKey {
    CUSTOMER_SUPPORT,
    RAG,
    CODING,
    REASONING,
    HALLUCINATION,
    SAFETY,
    SUMMARIZATION,
    TRANSLATION
}
