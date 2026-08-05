package com.broksforge.modules.brok.service;

import com.broksforge.modules.agent.domain.Agent;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokTurn;
import com.broksforge.modules.dataset.domain.Dataset;
import com.broksforge.modules.evaluation.domain.EvaluationJob;
import com.broksforge.modules.platform.web.dto.KnowledgeObject;
import com.broksforge.modules.prompt.domain.Prompt;
import com.broksforge.modules.provider.domain.Provider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A question, resolved against the engineering record: what is being asked, and what real object it is being
 * asked about.
 *
 * <p>Resolution is grounded rather than generative — an artifact enters the answer only because its actual
 * name appears in the question, because the workspace has it in focus, or because it is the only artifact of
 * the kind the question names. When a question names a kind that matches several artifacts, the parser
 * records the ambiguity instead of picking one; Brok then asks which, rather than answering
 * confidently about the wrong prompt.
 */
public record BrokQuestion(
        String raw,
        BrokIntent intent,
        /** The artifact the question is about, when one could be resolved honestly. */
        Subject subject,
        /** Set when the workspace focus is a knowledge object (a decision, claim, evidence, …). */
        KnowledgeObject focusKnowledge,
        /** Candidates when the question named a kind that matches several artifacts. */
        List<Subject> ambiguous,
        /** Revision labels named in the question, e.g. "v7" → 7. */
        List<Integer> revisions,
        /** The subject matter of a knowledge question, e.g. "hallucinations". */
        String topic,
        /** How far back the question looks. */
        Duration window,
        /**
         * The earlier question whose subject this one inherited, when the engineer did not restate it.
         * Brok says so in its reasoning rather than silently assuming — carrying context is a claim about
         * what was meant, and claims are always declared.
         */
        String carriedFrom
) {

    /** A real artifact the question resolved to. */
    public record Subject(String type, UUID entityId, String name, UUID projectId) {
        public String nodeId() {
            return type + ":" + entityId;
        }
    }

    private static final Pattern REVISION = Pattern.compile("\\bv(\\d{1,4})\\b");
    private static final int MIN_NAME_MATCH = 3;

    private static final Set<String> STOPWORDS = Set.of(
            "what", "which", "why", "how", "who", "when", "where", "the", "a", "an", "is", "are", "was", "were",
            "do", "does", "did", "we", "our", "us", "my", "me", "i", "it", "this", "that", "these", "those",
            "about", "for", "of", "in", "on", "to", "from", "and", "or", "any", "all", "show", "tell", "give",
            "know", "knowledge", "engineering", "exists", "exist", "there", "have", "has", "had", "with",
            "please", "can", "you", "your", "should", "would", "could", "much", "many", "most", "some");

    /** Kind words that let a question name a type without naming an artifact. */
    private static final List<String> TYPE_WORDS =
            List.of("evaluation", "prompt", "dataset", "agent", "provider");

    /** Parses a question with no conversation behind it (suggestions, context resolution). */
    public static BrokQuestion parse(String raw, String focus, BrokRecord record) {
        return parse(raw, focus, record, List.of());
    }

    /**
     * Parses and resolves a question against the record <em>and the conversation it sits in</em>.
     *
     * <p>This is what makes "Show me the evidence." a real question: when the engineer does not restate the
     * subject, the most recent subject in the conversation is inherited. The inheritance is recorded in
     * {@link #carriedFrom()} so the answer can declare it — an assumption Brok makes silently would be
     * exactly the kind of unstated claim the constitution forbids.
     *
     * @param focus   the workspace's current focus as a node id ({@code "prompt:<uuid>"}) or knowledge
     *                object id, or null
     * @param history the conversation so far, oldest first
     */
    public static BrokQuestion parse(String raw, String focus, BrokRecord record, List<BrokTurn> history) {
        String text = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        BrokIntent intent = BrokIntent.resolve(text);

        String effectiveFocus = focus;
        String inheritedFrom = null;
        if ((effectiveFocus == null || effectiveFocus.isBlank()) && history != null) {
            for (int i = history.size() - 1; i >= 0; i--) {
                BrokTurn turn = history.get(i);
                if (turn != null && turn.focus() != null && !turn.focus().isBlank()) {
                    effectiveFocus = turn.focus();
                    inheritedFrom = turn.question();
                    break;
                }
            }
        }

        KnowledgeObject focusKnowledge = null;
        Subject focusSubject = null;
        if (effectiveFocus != null && !effectiveFocus.isBlank()) {
            Optional<KnowledgeObject> knowledge = record.knowledgeById(effectiveFocus);
            if (knowledge.isPresent()) {
                focusKnowledge = knowledge.get();
                focusSubject = subjectOf(record, focusKnowledge.artifactType(), focusKnowledge.artifactEntityId());
            } else {
                focusSubject = subjectOfNode(record, effectiveFocus);
            }
        }

        // 1 — an artifact whose real name appears in the question always wins.
        Subject named = byName(record, text);

        // 2 — otherwise the workspace focus.
        Subject subject = named != null ? named : focusSubject;

        // 3 — otherwise, a kind named in the question, but only when it is unambiguous.
        List<Subject> ambiguous = List.of();
        if (subject == null) {
            String kind = TYPE_WORDS.stream().filter(text::contains).findFirst().orElse(null);
            if (kind != null) {
                List<Subject> candidates = ofType(record, kind);
                if (candidates.size() == 1) {
                    subject = candidates.get(0);
                } else if (candidates.size() > 1) {
                    ambiguous = candidates.stream().limit(6).toList();
                }
            }
        }

        // Context was only inherited if the question itself named nothing.
        String carriedFrom = named == null ? inheritedFrom : null;
        return new BrokQuestion(raw, intent, subject, focusKnowledge, ambiguous,
                revisionsIn(text), topicOf(text), windowOf(text), carriedFrom);
    }

    /** True when the question is about a specific object rather than the workspace as a whole. */
    public boolean hasSubject() {
        return subject != null;
    }

    // ------------------------------------------------------------------------------------------
    // Resolution helpers
    // ------------------------------------------------------------------------------------------

    private static Subject byName(BrokRecord record, String text) {
        List<Subject> matches = new ArrayList<>();
        for (Agent a : record.agents()) {
            addIfNamed(matches, text, "agent", a.getId(), a.getName(), a.getProjectId());
        }
        for (Prompt p : record.prompts()) {
            addIfNamed(matches, text, "prompt", p.getId(), p.getName(), p.getProjectId());
        }
        for (Dataset d : record.datasets()) {
            addIfNamed(matches, text, "dataset", d.getId(), d.getName(), d.getProjectId());
        }
        for (EvaluationJob j : record.jobs()) {
            addIfNamed(matches, text, "evaluation", j.getId(), j.getName(), j.getProjectId());
        }
        for (Provider p : record.providers()) {
            addIfNamed(matches, text, "provider", p.getId(), p.getName(), p.getProjectId());
        }
        // The longest matching name wins: "Support Prompt v2" must not resolve to a prompt called "Support".
        return matches.stream()
                .max(Comparator.comparingInt(s -> s.name().length()))
                .orElse(null);
    }

    private static void addIfNamed(List<Subject> out, String text, String type, UUID id, String name,
                                   UUID projectId) {
        if (name == null || name.length() < MIN_NAME_MATCH) {
            return;
        }
        if (text.contains(name.toLowerCase(Locale.ROOT))) {
            out.add(new Subject(type, id, name, projectId));
        }
    }

    private static List<Subject> ofType(BrokRecord record, String type) {
        return switch (type) {
            case "agent" -> record.agents().stream()
                    .map(a -> new Subject("agent", a.getId(), a.getName(), a.getProjectId())).toList();
            case "prompt" -> record.prompts().stream()
                    .map(p -> new Subject("prompt", p.getId(), p.getName(), p.getProjectId())).toList();
            case "dataset" -> record.datasets().stream()
                    .map(d -> new Subject("dataset", d.getId(), d.getName(), d.getProjectId())).toList();
            case "evaluation" -> record.jobs().stream()
                    .map(j -> new Subject("evaluation", j.getId(), j.getName(), j.getProjectId())).toList();
            case "provider" -> record.providers().stream()
                    .map(p -> new Subject("provider", p.getId(), p.getName(), p.getProjectId())).toList();
            default -> List.of();
        };
    }

    private static Subject subjectOfNode(BrokRecord record, String nodeId) {
        int colon = nodeId.indexOf(':');
        if (colon < 0) {
            return null;
        }
        try {
            return subjectOf(record, nodeId.substring(0, colon), UUID.fromString(nodeId.substring(colon + 1)));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Subject subjectOf(BrokRecord record, String type, UUID entityId) {
        if (type == null || entityId == null) {
            return null;
        }
        return switch (type) {
            case "agent" -> record.agent(entityId)
                    .map(a -> new Subject("agent", a.getId(), a.getName(), a.getProjectId())).orElse(null);
            case "prompt" -> record.prompt(entityId)
                    .map(p -> new Subject("prompt", p.getId(), p.getName(), p.getProjectId())).orElse(null);
            case "dataset" -> record.dataset(entityId)
                    .map(d -> new Subject("dataset", d.getId(), d.getName(), d.getProjectId())).orElse(null);
            case "evaluation" -> record.job(entityId)
                    .map(j -> new Subject("evaluation", j.getId(), j.getName(), j.getProjectId())).orElse(null);
            case "provider" -> record.provider(entityId)
                    .map(p -> new Subject("provider", p.getId(), p.getName(), p.getProjectId())).orElse(null);
            default -> null;
        };
    }

    private static List<Integer> revisionsIn(String text) {
        List<Integer> out = new ArrayList<>();
        Matcher matcher = REVISION.matcher(text);
        while (matcher.find()) {
            out.add(Integer.parseInt(matcher.group(1)));
        }
        return List.copyOf(out);
    }

    /** The words a knowledge question is actually about, with the question scaffolding removed. */
    private static String topicOf(String text) {
        String tail = text;
        int about = text.lastIndexOf(" about ");
        if (about >= 0) {
            tail = text.substring(about + 7);
        }
        Set<String> words = new LinkedHashSet<>();
        for (String word : tail.replaceAll("[^a-z0-9 ]", " ").split("\\s+")) {
            if (word.length() >= 3 && !STOPWORDS.contains(word)) {
                words.add(word);
            }
        }
        return String.join(" ", words);
    }

    private static Duration windowOf(String text) {
        if (text.contains("overnight") || text.contains("today") || text.contains("last night")) {
            return Duration.ofDays(1);
        }
        if (text.contains("yesterday")) {
            return Duration.ofDays(2);
        }
        if (text.contains("this month") || text.contains("last month")) {
            return Duration.ofDays(30);
        }
        return Duration.ofDays(7);
    }
}
