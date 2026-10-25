package com.pokesim.api.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Our own stable page shape, so the public JSON does not depend on Spring's internal Page serialization. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
