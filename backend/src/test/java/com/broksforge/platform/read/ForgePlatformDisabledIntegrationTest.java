package com.broksforge.platform.read;

import com.broksforge.modules.agent.domain.LlmProvider;
import com.broksforge.modules.prompt.service.PromptService;
import com.broksforge.modules.prompt.web.dto.PromptVersionResponse;
import com.broksforge.modules.provider.domain.Provider;
import com.broksforge.modules.provider.repository.ProviderRepository;
import com.broksforge.modules.provider.service.ProviderService;
import com.broksforge.modules.user.repository.UserRepository;
import com.broksforge.platform.ForgePlatform;
import com.broksforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Feature-flag-off / rollback verification (P6). With {@code broksforge.platform.v2.enabled=false} the entire
 * Platform V2 stack is dormant — no {@link ForgePlatform}, no {@link KnowledgeReadService}, no projection or
 * bridge beans — yet the application boots normally and <em>every</em> migrated read path (provider
 * get/list, prompt get-version/list-versions/for-execution) still returns the exact V1 value through the
 * always-present facade's central fallback. This proves the P0–P5 migration is fully reversible with a single
 * flag and never depends on Platform V2 for correctness.
 */
@TestPropertySource(properties = "broksforge.platform.v2.enabled=false")
class ForgePlatformDisabledIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ObjectProvider<ForgePlatform> forgePlatform;

    @Autowired
    private ObjectProvider<KnowledgeReadService> knowledgeReadService;

    @Autowired
    private KnowledgeReadFacade facade;

    @Autowired
    private ProviderService providerService;

    @Autowired
    private PromptService promptService;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void platformDormantButEveryMigratedReadPathReturnsV1() throws Exception {
        // The platform is fully dormant when disabled — no engine, no read service.
        assertNull(forgePlatform.getIfAvailable(), "ForgePlatform must not exist when the flag is off");
        assertNull(knowledgeReadService.getIfAvailable(), "KnowledgeReadService must not exist when the flag is off");

        // The facade is always present and transparently returns the V1 value (fallback owned centrally).
        assertEquals("V1 only", facade.providerName(UUID.randomUUID(), UUID.randomUUID(), "V1 only"));
        assertEquals("V1 template", facade.promptText(UUID.randomUUID(), UUID.randomUUID(), "V1 template"));

        // A real org/project/provider/prompt — no projection/backfill can run (platform disabled).
        String email = uniqueEmail();
        String token = registerAndGetToken(email, "StrongPass!2026");
        UUID actorId = userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
        UUID orgId = UUID.fromString(createOrg(token, "Disabled Org"));
        UUID projectId = UUID.fromString(createProject(token, orgId.toString(), "Disabled Project"));

        Provider provider = new Provider();
        provider.setOrganizationId(orgId);
        provider.setProjectId(projectId);
        provider.setName("Anthropic Prod");
        provider.setType(LlmProvider.ANTHROPIC);
        provider.setBaseUrl("https://api.anthropic.com");
        Provider saved = providerRepository.save(provider);

        UUID promptId = UUID.fromString(
                createPromptWithVersion(token, orgId.toString(), projectId.toString(), "Greeting"));
        String template = "Answer {{question}} for {{user}}";

        // Every migrated read path returns the exact V1 value with the platform disabled.
        assertEquals("Anthropic Prod", providerService.get(actorId, orgId, projectId, saved.getId()).name());
        assertEquals("Anthropic Prod", providerService
                .list(actorId, orgId, projectId, PageRequest.of(0, 20)).content().stream()
                .filter(p -> p.id().equals(saved.getId())).findFirst().orElseThrow().name());

        PromptVersionResponse forExecution =
                promptService.getVersionForExecution(actorId, orgId, projectId, promptId, null);
        assertEquals(template, forExecution.template());
        assertEquals(template, promptService
                .getVersion(actorId, orgId, projectId, promptId, forExecution.id()).template());
        assertEquals(template, promptService
                .listVersions(actorId, orgId, projectId, promptId, PageRequest.of(0, 20)).content().stream()
                .filter(v -> v.id().equals(forExecution.id())).findFirst().orElseThrow().template());
    }
}
