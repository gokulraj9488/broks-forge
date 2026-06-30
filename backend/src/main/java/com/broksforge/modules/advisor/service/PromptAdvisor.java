package com.broksforge.modules.advisor.service;

import com.broksforge.config.properties.AdvisorProperties;
import com.broksforge.modules.advisor.domain.Confidence;
import com.broksforge.modules.advisor.domain.Recommendation;
import com.broksforge.modules.advisor.domain.RecommendationCategory;
import com.broksforge.modules.advisor.domain.Severity;
import com.broksforge.modules.prompt.web.dto.PromptVersionResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyses a single prompt version and produces engineering recommendations. Pure and
 * deterministic (no I/O) like the evaluation metric engine, so it is trivially testable
 * and reusable. Detects bloat, token waste, excessive variables, redundant and
 * contradicting instructions, malformed variable syntax, injection exposure and
 * ambiguity — each as an actionable recommendation linked to the knowledge graph.
 */
@Component
public class PromptAdvisor {

    /** Single-brace placeholders that are NOT part of a {{double-brace}} variable. */
    private static final Pattern SINGLE_BRACE = Pattern.compile("(?<!\\{)\\{[a-zA-Z][a-zA-Z0-9_]*}(?!})");

    /** Opposing directive keyword groups; co-occurrence suggests a contradiction. */
    private static final List<String[]> CONTRADICTIONS = List.of(
            new String[]{"concise", "detailed"},
            new String[]{"concise", "comprehensive"},
            new String[]{"brief", "thorough"},
            new String[]{"only json", "explain"},
            new String[]{"one word", "step by step"});

    private final AdvisorProperties properties;

    public PromptAdvisor(AdvisorProperties properties) {
        this.properties = properties;
    }

    public List<Recommendation> analyze(PromptVersionResponse version) {
        List<Recommendation> recs = new ArrayList<>();
        String template = version.template() == null ? "" : version.template();
        String lower = template.toLowerCase(Locale.ROOT);
        int len = template.length();
        List<String> vars = version.variables() == null ? List.of() : version.variables();

        bloat(recs, len);
        excessiveVariables(recs, vars);
        redundancy(recs, template);
        contradictions(recs, lower);
        malformedVariables(recs, template);
        injectionRisk(recs, template, vars);
        ambiguity(recs, template, len, vars);

        return recs;
    }

    private void bloat(List<Recommendation> recs, int len) {
        if (len <= properties.promptMaxChars()) {
            return;
        }
        boolean severe = len > properties.promptMaxChars() * 2L;
        recs.add(Recommendation.builder(RecommendationCategory.PROMPT, "Prompt is large and likely wastes tokens")
                .why(("The template is %,d characters, above the %,d-character guideline. Large prompts inflate "
                        + "token cost and latency on every call and can dilute the model's attention on the actual task.")
                        .formatted(len, properties.promptMaxChars()))
                .whatChanged(null)
                .howToFix("Move static instructions into a reusable system message, delete redundant context, and keep "
                        + "only the variables that change per request. Consider summarising long background sections.")
                .expectedImprovement("Lower prompt-token cost and latency per call — typically a 10–30% token reduction.")
                .confidence(Confidence.HIGH)
                .severity(severe ? Severity.HIGH : Severity.MEDIUM)
                .evidence("Template length: %,d characters".formatted(len))
                .evidence("Guideline: %,d characters".formatted(properties.promptMaxChars()))
                .knowledgeKey(severe ? "TOKEN_BLOAT" : "PROMPT_BLOAT")
                .build());
    }

    private void excessiveVariables(List<Recommendation> recs, List<String> vars) {
        if (vars.size() <= properties.promptMaxVariables()) {
            return;
        }
        recs.add(Recommendation.builder(RecommendationCategory.PROMPT, "Prompt declares many variables")
                .why(("The template interpolates %d variables (guideline: %d). A high variable count makes the prompt "
                        + "harder to test and more likely to break when an upstream value is missing or malformed.")
                        .formatted(vars.size(), properties.promptMaxVariables()))
                .howToFix("Group related inputs into a single structured block, or split the prompt into composable "
                        + "sub-prompts so each has a focused, testable input surface.")
                .expectedImprovement("Fewer rendering edge cases and easier regression testing of the prompt.")
                .confidence(Confidence.MEDIUM)
                .severity(Severity.LOW)
                .evidence("Variables: " + String.join(", ", vars))
                .knowledgeKey("PROMPT_BLOAT")
                .build());
    }

    private void redundancy(List<Recommendation> recs, String template) {
        Map<String, Integer> seen = new HashMap<>();
        String duplicate = null;
        for (String raw : template.split("\\R")) {
            String norm = raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (norm.length() < 16) {
                continue;
            }
            int count = seen.merge(norm, 1, Integer::sum);
            if (count == 2 && duplicate == null) {
                duplicate = raw.trim();
            }
        }
        if (duplicate == null) {
            return;
        }
        recs.add(Recommendation.builder(RecommendationCategory.PROMPT, "Prompt repeats instructions")
                .why("At least one instruction line appears more than once. Repeated instructions add tokens without "
                        + "adding signal and can confuse the model about which directive takes precedence.")
                .howToFix("Remove the duplicate line and state each instruction exactly once.")
                .expectedImprovement("Cleaner prompt, slightly fewer tokens, less ambiguity for the model.")
                .confidence(Confidence.MEDIUM)
                .severity(Severity.LOW)
                .evidence("Repeated line: \"" + truncate(duplicate, 120) + "\"")
                .knowledgeKey("PROMPT_BLOAT")
                .build());
    }

    private void contradictions(List<Recommendation> recs, String lower) {
        for (String[] pair : CONTRADICTIONS) {
            if (lower.contains(pair[0]) && lower.contains(pair[1])) {
                recs.add(Recommendation.builder(RecommendationCategory.PROMPT, "Prompt has conflicting instructions")
                        .why(("The template asks for both \"%s\" and \"%s\". Conflicting directives force the model to "
                                + "guess which to honour, producing inconsistent outputs across runs.")
                                .formatted(pair[0], pair[1]))
                        .howToFix("Decide on the single intended behaviour and remove the conflicting instruction, or "
                                + "scope each directive to a clearly different part of the output.")
                        .expectedImprovement("More consistent, predictable outputs and fewer evaluation failures.")
                        .confidence(Confidence.MEDIUM)
                        .severity(Severity.MEDIUM)
                        .evidence("Conflicting terms: \"" + pair[0] + "\" and \"" + pair[1] + "\"")
                        .knowledgeKey("PROMPT_CONTRADICTION")
                        .build());
                return; // one contradiction finding is enough signal
            }
        }
    }

    private void malformedVariables(List<Recommendation> recs, String template) {
        Matcher matcher = SINGLE_BRACE.matcher(template);
        if (!matcher.find()) {
            return;
        }
        recs.add(Recommendation.builder(RecommendationCategory.PROMPT, "Possible malformed variable syntax")
                .why(("Found single-brace placeholder \"%s\". The platform only interpolates {{double-brace}} "
                        + "variables, so single-brace tokens are sent to the model literally and the intended value is "
                        + "never substituted — an effectively unused/never-filled variable.")
                        .formatted(matcher.group()))
                .howToFix("Use the {{variable}} syntax for every value you intend the platform to fill, or escape the "
                        + "brace if it is meant to be literal.")
                .expectedImprovement("Variables resolve as intended; eliminates a class of silent prompt bugs.")
                .confidence(Confidence.HIGH)
                .severity(Severity.MEDIUM)
                .evidence("Suspicious token: " + matcher.group())
                .knowledgeKey("PROMPT_INJECTION_RISK")
                .build());
    }

    private void injectionRisk(List<Recommendation> recs, String template, List<String> vars) {
        if (vars.isEmpty() || hasDelimiter(template)) {
            return;
        }
        recs.add(Recommendation.builder(RecommendationCategory.PROMPT, "User input is not delimited (injection risk)")
                .why("The template interpolates variables directly into the instructions without a delimiter around "
                        + "the untrusted value. A crafted input can then be read as instructions (prompt injection), "
                        + "overriding the intended behaviour.")
                .howToFix("Wrap each interpolated value in an explicit delimiter (e.g. triple backticks or XML-style "
                        + "tags) and instruct the model to treat the delimited content as data, not instructions.")
                .expectedImprovement("Materially reduces prompt-injection exposure and improves output reliability.")
                .confidence(Confidence.MEDIUM)
                .severity(Severity.HIGH)
                .evidence("Interpolated variables: " + String.join(", ", vars))
                .evidence("No delimiter (``` or <tags>) detected around variables")
                .knowledgeKey("PROMPT_INJECTION_RISK")
                .build());
    }

    private void ambiguity(List<Recommendation> recs, String template, int len, List<String> vars) {
        boolean tooTerse = len > 0 && len < 24;
        boolean noTaskCue = !template.contains("?")
                && !containsAny(template.toLowerCase(Locale.ROOT),
                "write", "summari", "classif", "extract", "answer", "generate", "translate",
                "list", "explain", "return", "produce", "rewrite", "analyse", "analyze");
        if (!tooTerse && !(noTaskCue && vars.isEmpty())) {
            return;
        }
        recs.add(Recommendation.builder(RecommendationCategory.PROMPT, "Prompt task is under-specified")
                .why("The template is very short or lacks an explicit task instruction, so the model must infer the "
                        + "intent. Ambiguous prompts produce high output variance and are hard to evaluate.")
                .howToFix("State the task with an explicit imperative (e.g. \"Summarise…\", \"Classify…\"), name the "
                        + "expected output format, and provide one example if the task is non-trivial.")
                .expectedImprovement("Lower output variance and a higher, more stable pass rate.")
                .confidence(Confidence.LOW)
                .severity(Severity.LOW)
                .evidence("Template length: " + len + " characters")
                .knowledgeKey("EXACT_MATCH_MISS")
                .build());
    }

    private boolean hasDelimiter(String template) {
        return template.contains("```") || template.contains("\"\"\"")
                || template.matches("(?s).*<[a-zA-Z][^>]*>.*</[a-zA-Z][^>]*>.*")
                || template.toLowerCase(Locale.ROOT).contains("delimit")
                || template.matches("(?s).*[-]{3,}.*");
    }

    private boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
