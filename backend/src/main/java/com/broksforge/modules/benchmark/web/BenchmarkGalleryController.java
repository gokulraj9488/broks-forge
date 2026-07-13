package com.broksforge.modules.benchmark.web;

import com.broksforge.modules.benchmark.service.BenchmarkGalleryService;
import com.broksforge.modules.benchmark.web.dto.BenchmarkGalleryDtos.GalleryTemplateResponse;
import com.broksforge.modules.benchmark.web.dto.BenchmarkGalleryDtos.ProvisionGalleryBenchmarkRequest;
import com.broksforge.modules.benchmark.web.dto.BenchmarkGalleryDtos.ProvisionGalleryBenchmarkResponse;
import com.broksforge.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The built-in Benchmark Gallery: curated templates that provision a starter dataset, prompt
 * and evaluation profile, then run them against a chosen agent — removing the "blank project"
 * problem for new users.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/benchmark-gallery")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Benchmark Gallery", description = "Curated benchmark templates: dataset + prompt + profile + one-click run")
public class BenchmarkGalleryController {

    private final BenchmarkGalleryService galleryService;

    public BenchmarkGalleryController(BenchmarkGalleryService galleryService) {
        this.galleryService = galleryService;
    }

    @GetMapping("/templates")
    @Operation(summary = "List built-in gallery templates")
    public ResponseEntity<List<GalleryTemplateResponse>> listTemplates(@PathVariable UUID organizationId,
                                                                       @PathVariable UUID projectId) {
        return ResponseEntity.ok(galleryService.listTemplates());
    }

    @PostMapping("/provision")
    @Operation(summary = "Provision a template",
            description = "Creates a dataset, prompt, and evaluation profile from the template, then runs an "
                    + "evaluation job against the given agent.")
    public ResponseEntity<ProvisionGalleryBenchmarkResponse> provision(@PathVariable UUID organizationId,
                                                                       @PathVariable UUID projectId,
                                                                       @Valid @RequestBody ProvisionGalleryBenchmarkRequest request) {
        ProvisionGalleryBenchmarkResponse response = galleryService.provision(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
