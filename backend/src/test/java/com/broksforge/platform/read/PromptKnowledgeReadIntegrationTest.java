package com.broksforge.platform.read;

import com.broksforge.modules.prompt.service.PromptService;
import com.broksforge.modules.prompt.web.dto.PromptVersionResponse;
import com.broksforge.modules.user.repository.UserRepository;
import com.broksforge.platform.projection.KnowledgeBackfillService;
import com.broksforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P4 parity proof for prompts: the Knowledge-backed read returns the same active-template text as V1, the
 * wired internal execution lookup ({@link PromptService#getVersionForExecution}) yields the identical
 * template, and the verified read-through falls back to V1 when Knowledge is stale or absent — so behavior is
 * always identical to V1.
 */
class PromptKnowledgeReadIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KnowledgeReadService knowledgeReads;

    @Autowired
    private KnowledgeReadFacade facade;

    @Autowired
    private KnowledgeBackfillService backfill;

    @Autowired
    private PromptService promptService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void promptTemplateReadMatchesV1AndFallsBackWhenInconsistent() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email, "StrongPass!2026");
        UUID actorId = userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
        UUID orgId = UUID.fromString(createOrg(token, "Prompt Org"));
        UUID projectId = UUID.fromString(createProject(token, orgId.toString(), "Prompt Project"));
        UUID promptId = UUID.fromString(
                createPromptWithVersion(token, orgId.toString(), projectId.toString(), "Greeting"));

        String template = "Answer {{question}} for {{user}}"; // the template the fixture activates

        backfill.backfillAll(); // projects the prompt active-template into Platform V2 Knowledge (idempotent)

        // Parity: the Knowledge-backed read returns the same active-template V1 holds.
        assertEquals(template, knowledgeReads.promptText(orgId, promptId).orElseThrow());

        // Consistent → the verified read-through serves the Knowledge value (identical to V1).
        assertEquals(template, facade.promptText(orgId, promptId, template));
        assertTrue(facade.isKnowledgeConsistentPromptText(orgId, promptId, template));

        // The wired internal lookup (used by evaluation/advisor execution) returns the identical template.
        PromptVersionResponse forExecution =
                promptService.getVersionForExecution(actorId, orgId, projectId, promptId, null);
        assertEquals(template, forExecution.template());

        // The wired single-version read path (P5) returns the identical template.
        UUID versionId = forExecution.id();
        assertEquals(template,
                promptService.getVersion(actorId, orgId, projectId, promptId, versionId).template());

        // The wired list-versions path (P5) returns the identical template for the active version.
        PromptVersionResponse fromList = promptService
                .listVersions(actorId, orgId, projectId, promptId, PageRequest.of(0, 20)).content().stream()
                .filter(v -> v.id().equals(versionId))
                .findFirst().orElseThrow();
        assertEquals(template, fromList.template());

        // Stale (V1 edited, not re-projected) → falls back to the current V1 value.
        assertEquals("Edited {{question}}", facade.promptText(orgId, promptId, "Edited {{question}}"));

        // Not projected → falls back to V1.
        assertEquals("whatever", facade.promptText(orgId, UUID.randomUUID(), "whatever"));
    }
}
