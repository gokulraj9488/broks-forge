package com.broksforge.modules.report.web;

import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.report.service.RenderedReport;
import com.broksforge.modules.report.service.ReportService;
import com.broksforge.modules.report.web.dto.ReportDtos.GenerateReportRequest;
import com.broksforge.modules.report.web.dto.ReportDtos.ReportResponse;
import com.broksforge.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Generate downloadable reports (JSON/CSV/HTML) from evaluation jobs, benchmarks and
 * regression checks, and list recently generated reports.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/reports")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Reports", description = "Generate and list report exports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/export")
    @Operation(summary = "Generate and download a report")
    public ResponseEntity<String> export(@PathVariable UUID organizationId,
                                         @PathVariable UUID projectId,
                                         @Valid @RequestBody GenerateReportRequest request) {
        RenderedReport rendered = reportService.generate(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, rendered.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + rendered.filename() + "\"")
                .body(rendered.content());
    }

    @GetMapping
    @Operation(summary = "List generated reports")
    public ResponseEntity<PageResponse<ReportResponse>> list(
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(reportService.list(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, pageable));
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "Get a report record")
    public ResponseEntity<ReportResponse> get(@PathVariable UUID organizationId,
                                              @PathVariable UUID projectId,
                                              @PathVariable UUID reportId) {
        return ResponseEntity.ok(reportService.get(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, reportId));
    }
}
