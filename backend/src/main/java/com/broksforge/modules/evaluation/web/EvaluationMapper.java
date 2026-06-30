package com.broksforge.modules.evaluation.web;

import com.broksforge.modules.evaluation.domain.EvaluationJob;
import com.broksforge.modules.evaluation.domain.EvaluationProfile;
import com.broksforge.modules.evaluation.domain.EvaluationResult;
import com.broksforge.modules.evaluation.domain.EvaluationRun;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobResponse;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobSummaryResponse;
import com.broksforge.modules.evaluation.web.dto.EvaluationProfileResponse;
import com.broksforge.modules.evaluation.web.dto.EvaluationResultResponse;
import com.broksforge.modules.evaluation.web.dto.EvaluationRunResponse;
import com.broksforge.modules.evaluation.web.dto.MetricSpecDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EvaluationMapper {

    EvaluationJobResponse toJobResponse(EvaluationJob job);

    EvaluationJobSummaryResponse toJobSummary(EvaluationJob job);

    EvaluationProfileResponse toProfileResponse(EvaluationProfile profile);

    MetricSpecDto toMetricSpecDto(MetricSpec spec);

    EvaluationRunResponse toRunResponse(EvaluationRun run);

    EvaluationResultResponse toResultResponse(EvaluationResult result);
}
