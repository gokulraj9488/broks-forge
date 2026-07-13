package com.broksforge.modules.evaluation.service;

import com.broksforge.modules.evaluation.domain.EvaluationJobEvent;
import com.broksforge.modules.evaluation.domain.EvaluationJobEventType;
import com.broksforge.modules.evaluation.repository.EvaluationJobEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Persists the execution engine's lifecycle events ({@link EvaluationJobEventType}) as an
 * append-only audit trail, and mirrors each one as a structured log line (job/org id in the
 * MDC) so the same events are searchable in both the database and centralized logging.
 * Runs in its own short transaction so a logging failure never aborts the caller's batch.
 */
@Slf4j
@Service
public class EvaluationJobEventService {

    private final EvaluationJobEventRepository repository;

    public EvaluationJobEventService(EvaluationJobEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID jobId, UUID organizationId, EvaluationJobEventType type, String message) {
        EvaluationJobEvent event = new EvaluationJobEvent();
        event.setEvaluationJobId(jobId);
        event.setOrganizationId(organizationId);
        event.setEventType(type);
        event.setMessage(message);
        repository.save(event);

        MDC.put("evaluationJobId", jobId.toString());
        MDC.put("organizationId", organizationId.toString());
        MDC.put("eventType", type.name());
        try {
            log.info("Evaluation job event: {}", message);
        } finally {
            MDC.remove("evaluationJobId");
            MDC.remove("organizationId");
            MDC.remove("eventType");
        }
    }
}
