package com.broksforge.modules.brok.web;

import com.broksforge.modules.brok.service.BrokBriefService;
import com.broksforge.modules.brok.service.BrokService;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokAnswer;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokAskRequest;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokBriefRef;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokContext;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokFollowUp;
import com.broksforge.modules.organization.service.OrganizationAccessService;
import com.broksforge.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Brok (P12) — the Engineering Partner.
 *
 * <p>Brok sits above the platform rather than beside it, so it gets its own namespace instead of
 * living under {@code /platform} (which publishes the Registry, the Forge Graph, AI Git and Engineering
 * Intelligence). It is gated by the same platform flag: when V2 is off the controller is simply absent, and
 * clients treat that as "Brok is not available here".
 *
 * <p>Every endpoint is <b>read-only</b>. {@code /ask} is a POST purely because a question plus its engineering
 * context is a request body, not because anything is written: no conversation is stored, no state mutates, and
 * asking the same question twice against an unchanged record produces the same answer. Conversation history
 * lives in the workspace, and the engineering context that matters ({@code projectId}, {@code focus}) travels
 * explicitly on every request — which is also what makes each answer independently reproducible and auditable.
 *
 * <p>Access is membership-gated exactly like the rest of the organization-scoped API, so Brok can never
 * reason across a boundary its caller cannot already see.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/brok")
@ConditionalOnProperty(prefix = "broksforge.platform.v2", name = "enabled", havingValue = "true")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Brok", description = "The Engineering Partner — grounded answers, briefs and recommendations")
public class BrokController {

    private final BrokService brok;
    private final BrokBriefService briefs;
    private final OrganizationAccessService accessService;

    public BrokController(BrokService brok,
                             BrokBriefService briefs,
                             OrganizationAccessService accessService) {
        this.brok = brok;
        this.briefs = briefs;
        this.accessService = accessService;
    }

    @PostMapping("/ask")
    @Operation(summary = "Ask Brok an engineering question",
            description = "Answers a question by reasoning over the organization's real engineering record — "
                    + "evaluations and their runs, version promotions, derived knowledge, and the Forge "
                    + "Graph's relationships. Read-only: nothing is stored and nothing mutates. Every answer "
                    + "carries a verdict, a reasoning chain in which each step declares whether it is derived "
                    + "or inferred, the evidence it rests on, the engineering impact, recommendations with a "
                    + "next action into an existing workflow, and follow-up investigations. Pass the "
                    + "conversation so far in 'history' and a follow-up inherits the subject of the question "
                    + "before it, so an engineer never repeats context. When the record cannot answer, Brok "
                    + "says so rather than improvising.")
    public ResponseEntity<BrokAnswer> ask(@PathVariable UUID organizationId,
                                             @Valid @RequestBody BrokAskRequest request) {
        UUID actorId = requireMember(organizationId);
        return ResponseEntity.ok(brok.ask(actorId, organizationId, request));
    }

    @GetMapping("/suggestions")
    @Operation(summary = "Questions worth asking right now",
            description = "Engineering-specific opening questions derived from what the record actually "
                    + "contains — failing evaluations, unsupported decisions, open investigations — so the "
                    + "workspace never opens on an empty prompt box.")
    public ResponseEntity<List<BrokFollowUp>> suggestions(@PathVariable UUID organizationId,
                                                             @RequestParam(required = false) UUID projectId,
                                                             @RequestParam(required = false) String focus) {
        requireMember(organizationId);
        return ResponseEntity.ok(brok.suggestions(organizationId, projectId, focus));
    }

    @GetMapping("/context")
    @Operation(summary = "The engineering context Brok has resolved",
            description = "What Brok understands about where you are: the organization, the project, "
                    + "the artifact or knowledge object in focus, and the graph nodes that context covers.")
    public ResponseEntity<BrokContext> context(@PathVariable UUID organizationId,
                                                  @RequestParam(required = false) UUID projectId,
                                                  @RequestParam(required = false) String focus) {
        requireMember(organizationId);
        return ResponseEntity.ok(brok.context(organizationId, projectId, focus));
    }

    @GetMapping("/briefs")
    @Operation(summary = "The Engineering Briefs available right now",
            description = "Lists the eight briefs with the headline each would lead with, and whether the "
                    + "record currently holds anything for it to report on.")
    public ResponseEntity<List<BrokBriefRef>> briefs(@PathVariable UUID organizationId,
                                                        @RequestParam(required = false) UUID projectId) {
        requireMember(organizationId);
        return ResponseEntity.ok(briefs.available(organizationId, projectId));
    }

    @GetMapping("/brief/{kind}")
    @Operation(summary = "Produce one Engineering Brief",
            description = "Writes one of the eight briefs (daily, deployment, incident, prompt-review, "
                    + "evaluation, dataset, knowledge, architecture) following the constitutional narrative: "
                    + "what happened, why, on what evidence, the engineering impact, the recommendation and "
                    + "the next action. Returns the same answer contract a question does.")
    public ResponseEntity<BrokAnswer> brief(@PathVariable UUID organizationId,
                                               @PathVariable String kind,
                                               @RequestParam(required = false) UUID projectId) {
        UUID actorId = requireMember(organizationId);
        return ResponseEntity.ok(briefs.brief(actorId, organizationId, projectId, kind));
    }

    private UUID requireMember(UUID organizationId) {
        UUID actorId = SecurityUtils.requireCurrentUserId();
        accessService.requireMembership(organizationId, actorId);
        return actorId;
    }
}
