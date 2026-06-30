package com.broksforge.modules.regression.service;

import com.broksforge.common.exception.BadRequestException;
import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.exception.ResourceNotFoundException;
import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.evaluation.service.EvaluationService;
import com.broksforge.modules.evaluation.service.SummaryMetrics;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobResponse;
import com.broksforge.modules.organization.domain.OrganizationRole;
import com.broksforge.modules.organization.service.OrganizationAccessService;
import com.broksforge.modules.project.service.ProjectService;
import com.broksforge.modules.regression.domain.RegressionCheck;
import com.broksforge.modules.regression.repository.RegressionCheckRepository;
import com.broksforge.modules.regression.web.dto.RegressionDtos.CreateRegressionCheckRequest;
import com.broksforge.modules.regression.web.dto.RegressionDtos.RegressionCheckResponse;
import com.broksforge.modules.regression.web.dto.RegressionDtos.RegressionCheckSummaryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Detects performance, cost, token and quality regressions of a candidate evaluation
 * job against a baseline. Each configured dimension is compared with direction
 * awareness (lower-is-better for latency/cost/tokens; higher-is-better for quality),
 * and flagged when the candidate is worse beyond the tolerance.
 */
@Slf4j
@Service
public class RegressionService {

    private static final BigDecimal DEFAULT_TOLERANCE = BigDecimal.TEN;

    /** Dimensions compared, in display order: summary metric key -> human label. */
    private static final List<Map.Entry<String, String>> DIMENSIONS = List.of(
            Map.entry("avgLatencyMs", "Latency"),
            Map.entry("totalCost", "Cost"),
            Map.entry("totalTokens", "Tokens"),
            Map.entry("passRate", "Quality (pass rate)"),
            Map.entry("avgScore", "Average score"));

    private final RegressionCheckRepository checkRepository;
    private final OrganizationAccessService accessService;
    private final ProjectService projectService;
    private final EvaluationService evaluationService;

    public RegressionService(RegressionCheckRepository checkRepository,
                             OrganizationAccessService accessService,
                             ProjectService projectService,
                             EvaluationService evaluationService) {
        this.checkRepository = checkRepository;
        this.accessService = accessService;
        this.projectService = projectService;
        this.evaluationService = evaluationService;
    }

    @Transactional
    public RegressionCheckResponse create(UUID actorId, UUID organizationId, UUID projectId,
                                          CreateRegressionCheckRequest request) {
        accessService.requireRole(organizationId, actorId, OrganizationRole.MEMBER);
        projectService.assertProjectExists(organizationId, projectId);

        if (request.baselineJobId().equals(request.candidateJobId())) {
            throw new BadRequestException(ErrorCode.REGRESSION_CONFIG_INVALID,
                    "Baseline and candidate jobs must be different");
        }
        EvaluationJobResponse baseline = evaluationService.get(actorId, organizationId, projectId,
                request.baselineJobId());
        EvaluationJobResponse candidate = evaluationService.get(actorId, organizationId, projectId,
                request.candidateJobId());

        BigDecimal tolerance = request.tolerancePct() != null ? request.tolerancePct() : DEFAULT_TOLERANCE;
        double tol = tolerance.doubleValue() / 100.0;

        Map<String, Object> findings = new LinkedHashMap<>();
        boolean anyRegressed = false;
        for (Map.Entry<String, String> dimension : DIMENSIONS) {
            String key = dimension.getKey();
            Double base = SummaryMetrics.value(baseline.summary(), key);
            Double cand = SummaryMetrics.value(candidate.summary(), key);
            boolean lowerIsBetter = !SummaryMetrics.higherIsBetter(key);

            Map<String, Object> finding = new LinkedHashMap<>();
            finding.put("label", dimension.getValue());
            finding.put("baseline", base);
            finding.put("candidate", cand);
            finding.put("lowerIsBetter", lowerIsBetter);

            boolean dimRegressed = false;
            if (base != null && cand != null) {
                Double deltaPct = base == 0.0 ? null : round((cand - base) / Math.abs(base) * 100.0);
                finding.put("deltaPct", deltaPct);
                dimRegressed = lowerIsBetter
                        ? cand > base * (1.0 + tol)
                        : cand < base * (1.0 - tol);
            } else {
                finding.put("deltaPct", null);
                finding.put("note", "Insufficient summary data for this dimension");
            }
            finding.put("regressed", dimRegressed);
            anyRegressed = anyRegressed || dimRegressed;
            findings.put(key, finding);
        }

        RegressionCheck check = new RegressionCheck();
        check.setOrganizationId(organizationId);
        check.setProjectId(projectId);
        check.setName(request.name().trim());
        check.setBaselineJobId(request.baselineJobId());
        check.setCandidateJobId(request.candidateJobId());
        check.setTolerancePct(tolerance);
        check.setRegressed(anyRegressed);
        check.setFindings(findings);
        RegressionCheck saved = checkRepository.save(check);

        log.info("Regression check {} created in project {} by {} (regressed={})",
                saved.getId(), projectId, actorId, anyRegressed);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<RegressionCheckSummaryResponse> list(UUID actorId, UUID organizationId, UUID projectId,
                                                             Pageable pageable) {
        accessService.requireMembership(organizationId, actorId);
        projectService.assertProjectExists(organizationId, projectId);
        return PageResponse.from(
                checkRepository.findByProjectIdAndDeletedFalseOrderByCreatedAtDesc(projectId, pageable),
                this::toSummary);
    }

    @Transactional(readOnly = true)
    public RegressionCheckResponse get(UUID actorId, UUID organizationId, UUID projectId, UUID checkId) {
        accessService.requireMembership(organizationId, actorId);
        return toResponse(load(organizationId, projectId, checkId));
    }

    @Transactional
    public void delete(UUID actorId, UUID organizationId, UUID projectId, UUID checkId) {
        accessService.requireRole(organizationId, actorId, OrganizationRole.ADMIN);
        RegressionCheck check = load(organizationId, projectId, checkId);
        check.softDelete(actorId);
    }

    /** Published for the dashboard: the most recent regressions in a project. */
    @Transactional(readOnly = true)
    public List<RegressionCheckSummaryResponse> recentRegressed(UUID actorId, UUID organizationId, UUID projectId) {
        accessService.requireMembership(organizationId, actorId);
        return checkRepository.findTop5ByProjectIdAndRegressedTrueAndDeletedFalseOrderByCreatedAtDesc(projectId)
                .stream().map(this::toSummary).toList();
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private RegressionCheckResponse toResponse(RegressionCheck check) {
        return new RegressionCheckResponse(check.getId(), check.getOrganizationId(), check.getProjectId(),
                check.getName(), check.getBaselineJobId(), check.getCandidateJobId(), check.getTolerancePct(),
                check.isRegressed(), check.getFindings(), check.getCreatedAt());
    }

    private RegressionCheckSummaryResponse toSummary(RegressionCheck check) {
        return new RegressionCheckSummaryResponse(check.getId(), check.getName(), check.getBaselineJobId(),
                check.getCandidateJobId(), check.isRegressed(), check.getCreatedAt());
    }

    private RegressionCheck load(UUID organizationId, UUID projectId, UUID checkId) {
        return checkRepository.findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(checkId, projectId, organizationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Regression check", checkId));
    }

    private Double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
