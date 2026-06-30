package com.broksforge.modules.agent.repository;

import com.broksforge.modules.agent.domain.Agent;
import com.broksforge.modules.agent.domain.AgentFramework;
import com.broksforge.modules.agent.domain.AgentHealthStatus;
import com.broksforge.modules.agent.domain.AgentLanguage;
import com.broksforge.modules.agent.domain.AgentStatus;
import com.broksforge.modules.agent.domain.AgentTag;
import com.broksforge.modules.agent.domain.AgentVisibility;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Composable JPA {@link Specification}s for searching and filtering agents.
 * Keeping these here means new filters are added without changing the service or
 * controller, and the dynamic-query logic stays out of the persistence-agnostic
 * service layer.
 */
public final class AgentSpecifications {

    private AgentSpecifications() {
    }

    public static Specification<Agent> build(UUID projectId,
                                             String query,
                                             AgentFramework framework,
                                             AgentLanguage language,
                                             AgentVisibility visibility,
                                             AgentStatus status,
                                             AgentHealthStatus healthStatus,
                                             String tag) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("projectId"), projectId));
            predicates.add(cb.isFalse(root.get("deleted")));

            if (framework != null) {
                predicates.add(cb.equal(root.get("framework"), framework));
            }
            if (language != null) {
                predicates.add(cb.equal(root.get("language"), language));
            }
            if (visibility != null) {
                predicates.add(cb.equal(root.get("visibility"), visibility));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (healthStatus != null) {
                predicates.add(cb.equal(root.get("healthStatus"), healthStatus));
            }
            if (StringUtils.hasText(query)) {
                String like = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("slug")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            if (StringUtils.hasText(tag)) {
                Subquery<UUID> tagSubquery = criteriaQuery.subquery(UUID.class);
                Root<AgentTag> tagRoot = tagSubquery.from(AgentTag.class);
                tagSubquery.select(tagRoot.get("agentId"))
                        .where(cb.equal(cb.lower(tagRoot.get("label")), tag.trim().toLowerCase(Locale.ROOT)));
                predicates.add(root.get("id").in(tagSubquery));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
