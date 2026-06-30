package com.broksforge.modules.dataset.service;

import java.util.Map;
import java.util.UUID;

/**
 * Published, id-referenced view of a dataset item for execution by sibling modules
 * (evaluation, benchmarking). Lets those modules consume dataset rows without
 * importing the dataset module's JPA entities.
 */
public record DatasetRow(
        UUID itemId,
        int sequence,
        String input,
        String expectedOutput,
        Map<String, Object> metadata
) {
}
