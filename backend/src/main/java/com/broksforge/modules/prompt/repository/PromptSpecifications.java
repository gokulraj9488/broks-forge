package com.broksforge.modules.prompt.repository;

import com.broksforge.modules.prompt.domain.Prompt;
import com.broksforge.modules.prompt.domain.PromptStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Composable JPA {@link Specification}s for prompt search and filtering. Tag
 * filtering matches the JSON-encoded {@code tags} column (lowercase JSON array).
 */
public final class PromptSpecifications {

    private PromptSpecifications() {
    }

    public static Specification<Prompt> build(UUID projectId, String query, PromptStatus status, String tag) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("projectId"), projectId));
            predicates.add(cb.isFalse(root.get("deleted")));

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
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
