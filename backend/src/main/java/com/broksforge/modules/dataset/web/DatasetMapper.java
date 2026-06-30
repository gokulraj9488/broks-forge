package com.broksforge.modules.dataset.web;

import com.broksforge.modules.dataset.domain.Dataset;
import com.broksforge.modules.dataset.domain.DatasetItem;
import com.broksforge.modules.dataset.domain.DatasetVersion;
import com.broksforge.modules.dataset.web.dto.DatasetItemResponse;
import com.broksforge.modules.dataset.web.dto.DatasetResponse;
import com.broksforge.modules.dataset.web.dto.DatasetSummaryResponse;
import com.broksforge.modules.dataset.web.dto.DatasetVersionResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DatasetMapper {

    DatasetResponse toResponse(Dataset dataset);

    DatasetSummaryResponse toSummary(Dataset dataset);

    DatasetVersionResponse toVersionResponse(DatasetVersion version);

    DatasetItemResponse toItemResponse(DatasetItem item);
}
