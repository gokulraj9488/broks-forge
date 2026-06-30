package com.broksforge.modules.dataset.repository;

import com.broksforge.modules.dataset.domain.Dataset;
import com.broksforge.modules.dataset.domain.DatasetStatus;
import com.broksforge.modules.dataset.domain.DatasetVisibility;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Composable JPA {@link Specification}s for dataset search and filtering. Tag
 * filtering matches against the JSON-encoded {@code tags} column (tags are stored
 * as a lowercase JSON array, so a {@code "tag"} substring is an exact tag match).
 */
public final class DatasetSpecifications {

    private DatasetSpecifications() {
    }

    public static Specification<Dataset> build(UUID projectId,
                                               String query,
                                               DatasetStatus status,
                                               DatasetVisibility visibility,
                                               String tag) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("projectId"), projectId));
            predicates.add(cb.isFalse(root.get("deleted")));

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (visibility != null) {
                predicates.add(cb.equal(root.get("visibility"), visibility));
            }
            if (StringUtils.hasText(query)) {
                String like = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("slug")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            if (StringUtils.hasText(tag)) {
                String needle = "%\"" + tag.trim().toLowerCase(Locale.ROOT) + "\"%";
                predicates.add(cb.like(cb.lower(root.get("tags")), needle));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
