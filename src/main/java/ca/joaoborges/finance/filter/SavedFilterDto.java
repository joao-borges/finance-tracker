package ca.joaoborges.finance.filter;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
public record SavedFilterDto(
        Long id,
        String name,
        LocalDate fromDate,
        LocalDate toDate,
        List<Long> accountIds,
        List<Long> merchantIds,
        List<Long> categoryIds,
        List<String> tags,
        Boolean review) {
}
