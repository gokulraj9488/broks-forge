package com.broksforge.platform.projection;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.ActorId;
import com.broksforge.kernel.api.Name;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.platform.ForgePlatform;
import com.broksforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Backfill reads existing V1 data (a seeded prompt) and projects it lawfully and idempotently. */
class KnowledgeBackfillIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KnowledgeBackfillService backfill;

    @Autowired
    private ForgePlatform platform;

    @Test
    void backfillsExistingPromptIdempotently() throws Exception {
        String token = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        String orgId = createOrg(token, "Backfill Org");
        String projectId = createProject(token, orgId, "Backfill Project");
        String promptId = createPromptWithVersion(token, orgId, projectId, "Greeting");

        BackfillSummary first = backfill.backfillAll();
        assertTrue(first.prompts() >= 1, "the seeded prompt must be projected");

        OrgId org = platform.identity().toOrgId(UUID.fromString(orgId));
        var kernel = platform.clientFor(org, ActorId.of("test:verify")).repository().kernel();
        Optional<Address.Revision> projected = kernel.resolve(org, Name.of("v1/prompt/" + promptId));
        assertTrue(projected.isPresent(), "the prompt must be resolvable by its V1-derived name");

        NodeId firstNode = projected.get().node();
        backfill.backfillAll(); // second pass
        Optional<Address.Revision> again = kernel.resolve(org, Name.of("v1/prompt/" + promptId));
        assertTrue(again.isPresent());
        assertEquals(firstNode, again.get().node(), "re-running backfill must not duplicate the artifact");
    }
}
