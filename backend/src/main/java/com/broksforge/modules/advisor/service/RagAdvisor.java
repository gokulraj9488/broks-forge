package com.broksforge.modules.advisor.service;

import com.broksforge.modules.advisor.domain.Confidence;
import com.broksforge.modules.advisor.domain.Recommendation;
import com.broksforge.modules.advisor.domain.RecommendationCategory;
import com.broksforge.modules.advisor.domain.Severity;
import com.broksforge.modules.agent.web.dto.AgentCapabilitiesDto;
import com.broksforge.modules.agent.web.dto.AgentResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Analyses an agent's declared retrieval (RAG) configuration and recommends
 * improvements: poor chunk size, missing overlap, low similarity threshold, oversized
 * top-k and missing embedding model.
 *
 * <p>The platform does not yet capture live retrieval traces (queries, retrieved
 * chunks, scores) — that is the Phase 5 RAG inspector. So this advisor reads the
 * <em>declared</em> retrieval settings the team records in the agent's capability
 * metadata, and is explicit when that configuration is absent. This keeps every
 * recommendation grounded in real, stored data while the deeper retrieval signal is
 * still ahead of us.</p>
 */
@Component
public class RagAdvisor {

    public List<Recommendation> analyze(AgentResponse agent) {
        AgentCapabilitiesDto caps = agent.capabilities();
        if (caps == null || !caps.rag()) {
            return List.of();
        }
        Map<String, Object> meta = caps.customMetadata() == null ? Map.of() : caps.customMetadata();

        Double chunkSize = number(meta, "chunkSize", "chunk_size");
        Double chunkOverlap = number(meta, "chunkOverlap", "chunk_overlap");
        Double topK = number(meta, "topK", "top_k");
        Double similarity = number(meta, "similarityThreshold", "similarity_threshold");
        String embeddingModel = string(meta, "embeddingModel", "embedding_model");

        boolean anyConfig = chunkSize != null || chunkOverlap != null || topK != null
                || similarity != null || embeddingModel != null;

        if (!anyConfig) {
            return List.of(notInstrumented());
        }

        List<Recommendation> recs = new java.util.ArrayList<>();
        chunkSize(recs, chunkSize);
        overlap(recs, chunkSize, chunkOverlap);
        topK(recs, topK);
        similarity(recs, similarity);
        embedding(recs, embeddingModel);
        return recs;
    }

    private Recommendation notInstrumented() {
        return Recommendation.builder(RecommendationCategory.RAG, "RAG is enabled but retrieval is not described")
                .why("This agent declares RAG, but no retrieval configuration (chunk size, overlap, top-k, similarity "
                        + "threshold, embedding model) is recorded, and the platform does not yet capture live "
                        + "retrieval traces. Retrieval quality is therefore invisible — and retrieval is the most "
                        + "common root cause of wrong-but-confident answers.")
                .howToFix("Record the retrieval settings in the agent's capability metadata (chunkSize, chunkOverlap, "
                        + "topK, similarityThreshold, embeddingModel) so they can be reviewed now, and adopt the RAG "
                        + "inspector when it lands to capture per-query retrieval traces.")
                .expectedImprovement("Makes retrieval configuration reviewable today and trace-ready for Phase 5.")
                .confidence(Confidence.LOW)
                .severity(Severity.LOW)
                .evidence("capabilities.rag = true; no retrieval metadata present")
                .knowledgeKey("RAG_LOW_SIMILARITY")
                .build();
    }

    private void chunkSize(List<Recommendation> recs, Double chunkSize) {
        if (chunkSize == null || chunkSize <= 1500) {
            return;
        }
        recs.add(Recommendation.builder(RecommendationCategory.RAG, "Retrieval chunks are oversized")
                .why(("Configured chunk size is %,.0f. Very large chunks retrieve broad, low-precision context: the "
                        + "relevant passage is diluted by surrounding text, lowering grounding and wasting tokens.")
                        .formatted(chunkSize))
                .howToFix("Reduce chunk size to roughly 300–800 tokens and re-index, so each chunk is a focused, "
                        + "semantically coherent passage.")
                .expectedImprovement("Higher retrieval precision and grounding; fewer tokens per query.")
                .confidence(Confidence.MEDIUM)
                .severity(Severity.MEDIUM)
                .evidence("chunkSize = " + format(chunkSize))
                .knowledgeKey("RAG_CHUNK_OVERSIZED")
                .build());
    }

    private void overlap(List<Recommendation> recs, Double chunkSize, Double chunkOverlap) {
        if (chunkSize == null) {
            return;
        }
        boolean noOverlap = chunkOverlap == null || chunkOverlap <= 0;
        if (!noOverlap) {
            return;
        }
        recs.add(Recommendation.builder(RecommendationCategory.RAG, "Retrieval chunks have no overlap")
                .why("Chunking with zero overlap can split a relevant passage across a boundary, so neither chunk "
                        + "contains the full answer and retrieval misses it.")
                .howToFix("Add 10–20% overlap between adjacent chunks so boundary-spanning passages remain intact.")
                .expectedImprovement("Fewer boundary misses; more reliable retrieval of complete passages.")
                .confidence(Confidence.MEDIUM)
                .severity(Severity.LOW)
                .evidence("chunkOverlap = " + (chunkOverlap == null ? "unset" : format(chunkOverlap)))
                .knowledgeKey("RAG_CHUNK_OVERSIZED")
                .build());
    }

    private void topK(List<Recommendation> recs, Double topK) {
        if (topK == null || topK <= 12) {
            return;
        }
        recs.add(Recommendation.builder(RecommendationCategory.RAG, "Top-k retrieval is high")
                .why(("Retrieving top-%.0f chunks per query floods the context with marginally-relevant passages, "
                        + "increasing tokens and giving the model more chances to ground on the wrong source.")
                        .formatted(topK))
                .howToFix("Lower top-k (often 3–6 is enough) and rely on a good similarity threshold and re-ranking "
                        + "rather than sheer volume.")
                .expectedImprovement("Lower token cost and less distraction from off-topic context.")
                .confidence(Confidence.MEDIUM)
                .severity(Severity.LOW)
                .evidence("topK = " + format(topK))
                .knowledgeKey("RAG_LOW_SIMILARITY")
                .build());
    }

    private void similarity(List<Recommendation> recs, Double similarity) {
        if (similarity == null || similarity >= 0.7) {
            return;
        }
        recs.add(Recommendation.builder(RecommendationCategory.RAG, "Similarity threshold is low")
                .why(("A similarity threshold of %.2f admits weakly-related chunks into the context, which is a leading "
                        + "cause of confidently wrong, poorly-grounded answers.")
                        .formatted(similarity))
                .howToFix("Raise the threshold (e.g. ≥ 0.75 for cosine similarity) so only strongly-relevant chunks are "
                        + "used, and return fewer-but-better passages.")
                .expectedImprovement("Better grounding and fewer hallucinated answers.")
                .confidence(Confidence.MEDIUM)
                .severity(Severity.MEDIUM)
                .evidence("similarityThreshold = " + format(similarity))
                .knowledgeKey("RAG_LOW_SIMILARITY")
                .build());
    }

    private void embedding(List<Recommendation> recs, String embeddingModel) {
        if (embeddingModel != null && !embeddingModel.isBlank()) {
            return;
        }
        recs.add(Recommendation.builder(RecommendationCategory.RAG, "Embedding model is unspecified")
                .why("No embedding model is recorded for retrieval. Embedding quality determines what can be retrieved "
                        + "at all; an unknown or weak model silently caps retrieval quality.")
                .howToFix("Record the embedding model in capability metadata and ensure query and index embeddings use "
                        + "the same, current model.")
                .expectedImprovement("Makes retrieval quality attributable and reproducible.")
                .confidence(Confidence.LOW)
                .severity(Severity.LOW)
                .evidence("embeddingModel = unset")
                .knowledgeKey("RAG_LOW_SIMILARITY")
                .build());
    }

    private Double number(Map<String, Object> meta, String... keys) {
        for (String key : keys) {
            Object value = meta.get(key);
            if (value instanceof Number n) {
                return n.doubleValue();
            }
            if (value instanceof String s && !s.isBlank()) {
                try {
                    return Double.parseDouble(s.trim());
                } catch (NumberFormatException ignored) {
                    // fall through to next key
                }
            }
        }
        return null;
    }

    private String string(Map<String, Object> meta, String... keys) {
        for (String key : keys) {
            Object value = meta.get(key);
            if (value instanceof String s && !s.isBlank()) {
                return s.trim();
            }
        }
        return null;
    }

    private String format(double value) {
        if (value == Math.floor(value)) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
