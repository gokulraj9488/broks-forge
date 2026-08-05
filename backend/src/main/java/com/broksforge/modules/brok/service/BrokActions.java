package com.broksforge.modules.brok.service;

import com.broksforge.modules.brok.web.dto.BrokDtos.BrokAction;

import java.util.UUID;

/**
 * The engineering workflows an answer can continue into.
 *
 * <p>Every action names a surface that <b>already exists</b> — the Forge Graph, an artifact's Execution Graph,
 * Engineering Intelligence, Evolution, AI Git revisions, a revision comparison, Knowledge, the Registry,
 * Analytics, Insights. Brok never invents a destination; it hands the engineer back into the platform
 * at the exact place the answer was derived from, which is what stops it from becoming a chat window bolted
 * onto a product.
 *
 * <p>Actions carry ids rather than URLs so route shape stays owned by the client (which already maps artifacts
 * to their workspaces) and the API stays free of presentation concerns.
 */
public final class BrokActions {

    private BrokActions() {
    }

    /** Opens the Forge Graph focused on one node. */
    public static BrokAction openGraph(String nodeId, String label) {
        return new BrokAction("openGraph", label, null, null, null, nodeId, null);
    }

    /**
     * Starts a fresh investigation in Brok, focused on one object and opening with a specific question.
     * An investigation is not a new surface — it is this same workspace, entered deliberately about one
     * thing, which is what turns a recommendation into work rather than advice.
     */
    public static BrokAction startInvestigation(String nodeId, String label, String question) {
        return new BrokAction("startInvestigation", label, null, null, null, nodeId, question);
    }

    /** Opens an evaluation's Execution Graph — the whole chain, healthy stages included. */
    public static BrokAction openExecutionGraph(UUID evaluationId, UUID projectId, String label) {
        return new BrokAction("openExecutionGraph", label, "evaluation", evaluationId, projectId, null, null);
    }

    /**
     * Opens the same graph already narrowed to the broken links — the Failure Graph.
     *
     * <p>One model serves both views (a failure is an execution whose stages are in an error state), so this
     * is not a second surface; it is the same surface entered in the state the answer is about. Sending an
     * engineer to a graph they then have to filter themselves would be handing back work Brok already did.
     */
    public static BrokAction openFailureGraph(UUID evaluationId, UUID projectId, String label) {
        return new BrokAction("openFailureGraph", label, "evaluation", evaluationId, projectId, null, null);
    }

    /** Opens an evaluation's workspace. */
    public static BrokAction openEvaluation(UUID evaluationId, UUID projectId, String label) {
        return new BrokAction("openEvaluation", label, "evaluation", evaluationId, projectId, null, null);
    }

    /** Opens an artifact's Engineering Intelligence — what was observed, claimed, decided and evidenced. */
    public static BrokAction openIntelligence(String type, UUID entityId, UUID projectId, String label) {
        return new BrokAction("openIntelligence", label, type, entityId, projectId, null, null);
    }

    /** Opens an artifact's Evolution — lineage, dependents and impact. */
    public static BrokAction openEvolution(String type, UUID entityId, UUID projectId, String label) {
        return new BrokAction("openEvolution", label, type, entityId, projectId, null, null);
    }

    /** Opens AI Git for an artifact — its real revision timeline. */
    public static BrokAction openRevisions(String type, UUID entityId, UUID projectId, String label) {
        return new BrokAction("openRevisions", label, type, entityId, projectId, null, null);
    }

    /** Opens AI Git with the comparison view for two revisions of the same artifact. */
    public static BrokAction compareRevisions(String type, UUID entityId, UUID projectId, String label) {
        return new BrokAction("compareRevisions", label, type, entityId, projectId, null, null);
    }

    /** Opens one engineering-knowledge object's own page. */
    public static BrokAction openKnowledge(String knowledgeId, String label) {
        return new BrokAction("openKnowledge", label, null, null, null, knowledgeId, null);
    }

    public static BrokAction openRegistry(String label) {
        return new BrokAction("openRegistry", label, null, null, null, null, null);
    }

    public static BrokAction openAnalytics(String label) {
        return new BrokAction("openAnalytics", label, null, null, null, null, null);
    }

    public static BrokAction openInsights(String label) {
        return new BrokAction("openInsights", label, null, null, null, null, null);
    }
}
