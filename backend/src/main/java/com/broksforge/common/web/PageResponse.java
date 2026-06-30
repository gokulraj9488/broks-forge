package com.broksforge.common.web;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Transport-friendly representation of a Spring Data {@link Page}. Avoids
 * leaking the full {@code PageImpl} structure (which is not a stable API) to
 * clients.
 *
 * @param <T> element type
 */
@Schema(name = "PageResponse", description = "A page of results with pagination metadata")
public record PageResponse<T>(
        @Schema(description = "Page contents") List<T> content,
        @Schema(description = "Zero-based page index", example = "0") int page,
        @Schema(description = "Requested page size", example = "20") int size,
        @Schema(description = "Total number of elements across all pages", example = "137") long totalElements,
        @Schema(description = "Total number of pages", example = "7") int totalPages,
        @Schema(description = "Whether this is the first page") boolean first,
        @Schema(description = "Whether this is the last page") boolean last,
        @Schema(description = "Whether a further page exists") boolean hasNext
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.hasNext()
        );
    }

    /**
     * Maps the underlying entity page to a DTO page in one step.
     */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.hasNext()
        );
    }
}
