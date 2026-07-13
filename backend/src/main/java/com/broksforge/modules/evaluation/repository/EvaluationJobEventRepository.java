package com.broksforge.modules.evaluation.repository;

import com.broksforge.modules.evaluation.domain.EvaluationJobEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EvaluationJobEventRepository extends JpaRepository<EvaluationJobEvent, UUID> {

    List<EvaluationJobEvent> findByEvaluationJobIdOrderByCreatedAtDesc(UUID evaluationJobId, Pageable pageable);
}
