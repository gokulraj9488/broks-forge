package com.broksforge.platform.projection;

import com.broksforge.fxp.ForgeClient;
import com.broksforge.kernel.api.ActorId;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.knowledge.graph.KnowledgeObject;
import com.broksforge.modules.agent.domain.Agent;
import com.broksforge.modules.agent.repository.AgentRepository;
import com.broksforge.modules.prompt.domain.Prompt;
import com.broksforge.modules.prompt.domain.PromptVersion;
import com.broksforge.modules.prompt.repository.PromptRepository;
import com.broksforge.modules.prompt.repository.PromptVersionRepository;
import com.broksforge.modules.provider.domain.Provider;
import com.broksforge.modules.provider.repository.ProviderRepository;
import com.broksforge.platform.ForgePlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Idempotent backfill: reads existing Broks Forge configuration entities (read-only, via their
 * repositories) and projects them into Platform V2 Knowledge through {@link KnowledgeProjectionService}.
 *
 * <p>Read-JPA → write-V2 only. It never mutates application data, never changes existing business logic, and
 * (because projection is Name-idempotent) can be re-run safely without duplicating artifacts. Providers,
 * their concrete model ids (as attached to agents), and prompts' active templates are projected; the V1
 * "agent" endpoint contributes only its {@code Model} (per the approved mapping — no V2 Agent).
 */
@Service
@ConditionalOnProperty(prefix = "broksforge.platform.v2", name = "enabled", havingValue = "true")
public class KnowledgeBackfillService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBackfillService.class);
    private static final ActorId PROJECTION_ACTOR = ActorId.of("system:forge-projection");

    private final ForgePlatform platform;
    private final KnowledgeProjectionService projection;
    private final ProviderRepository providerRepository;
    private final AgentRepository agentRepository;
    private final PromptRepository promptRepository;
    private final PromptVersionRepository promptVersionRepository;

    public KnowledgeBackfillService(ForgePlatform platform, KnowledgeProjectionService projection,
                                    ProviderRepository providerRepository, AgentRepository agentRepository,
                                    PromptRepository promptRepository, PromptVersionRepository promptVersionRepository) {
        this.platform = platform;
        this.projection = projection;
        this.providerRepository = providerRepository;
        this.agentRepository = agentRepository;
        this.promptRepository = promptRepository;
        this.promptVersionRepository = promptVersionRepository;
    }

    /** Project all existing providers, agent-attached models, and prompt active-templates. Idempotent. */
    public BackfillSummary backfillAll() {
        int providers = 0;
        int models = 0;
        int prompts = 0;

        for (Provider provider : providerRepository.findAll()) {
            try {
                ForgeClient client = clientFor(provider.getOrganizationId());
                projection.projectProvider(client, provider.getId(), provider.getName());
                providers++;
            } catch (RuntimeException e) {
                log.warn("skipped provider {} during backfill: {}", provider.getId(), e.toString());
            }
        }

        for (Agent agent : agentRepository.findAll()) {
            if (agent.getProviderId() == null || agent.getModelOverride() == null) {
                continue; // no lawful (provider, model) pair to project
            }
            try {
                Provider provider = providerRepository.findById(agent.getProviderId()).orElse(null);
                if (provider == null) {
                    continue;
                }
                ForgeClient client = clientFor(agent.getOrganizationId());
                KnowledgeObject providerArtifact = projection.projectProvider(client, provider.getId(), provider.getName());
                projection.projectModel(client, provider.getId(), providerArtifact, agent.getModelOverride());
                models++;
            } catch (RuntimeException e) {
                log.warn("skipped model for agent {} during backfill: {}", agent.getId(), e.toString());
            }
        }

        for (Prompt prompt : promptRepository.findAll()) {
            try {
                String text = activeTemplate(prompt.getId());
                if (text == null) {
                    continue; // no version text to project
                }
                ForgeClient client = clientFor(prompt.getOrganizationId());
                projection.projectPrompt(client, prompt.getId(), text);
                prompts++;
            } catch (RuntimeException e) {
                log.warn("skipped prompt {} during backfill: {}", prompt.getId(), e.toString());
            }
        }

        BackfillSummary summary = new BackfillSummary(providers, models, prompts);
        log.info("Forge Knowledge backfill complete (idempotent): {}", summary);
        return summary;
    }

    private String activeTemplate(UUID promptId) {
        return promptVersionRepository.findByPromptIdAndActiveTrue(promptId).stream()
                .findFirst()
                .or(() -> promptVersionRepository.findFirstByPromptIdOrderByVersionNumberDesc(promptId))
                .map(PromptVersion::getTemplate)
                .orElse(null);
    }

    private ForgeClient clientFor(UUID organizationId) {
        OrgId org = platform.identity().toOrgId(organizationId);
        return platform.clientFor(org, PROJECTION_ACTOR);
    }
}
