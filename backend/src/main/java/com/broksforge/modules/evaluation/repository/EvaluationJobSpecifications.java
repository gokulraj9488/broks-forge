package com.broksforge.modules.evaluation.repository;

import com.broksforge.modules.evaluation.domain.EvaluationJob;
import com.broksforge.modules.evaluation.domain.EvaluationStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Composable JPA {@link Specification}s for evaluation job search and filtering.
 */
public final class EvaluationJobSpecifications {

    private EvaluationJobSpecifications() {
    }

    public static Specification<EvaluationJob> build(UUID projectId,
                                                     String query,
                                                     EvaluationStatus status,
                                                     UUID agentId,
                                                     UUID datasetId) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("projectId"), projectId));
            predicates.add(cb.isFalse(root.get("deleted")));

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (agentId != null) {
                predicates.add(cb.equal(root.get("agentId"), agentId));
            }
            if (datasetId != null) {
                predicates.add(cb.equal(root.get("datasetId"), datasetId));
            }
            if (StringUtils.hasText(query)) {
                String like = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), like));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
