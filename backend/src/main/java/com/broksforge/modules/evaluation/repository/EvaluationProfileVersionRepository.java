package com.broksforge.modules.evaluation.repository;

import com.broksforge.modules.evaluation.domain.EvaluationProfileVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EvaluationProfileVersionRepository extends JpaRepository<EvaluationProfileVersion, UUID> {

    Optional<EvaluationProfileVersion> findByIdAndProfileId(UUID id, UUID profileId);
}
