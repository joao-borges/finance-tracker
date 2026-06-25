package ca.joaoborges.finance.category;

import lombok.Builder;

@Builder
public record CategoryGroupDto(
        Long id,
        String name,
        Integer sortOrder,
        String icon,
        String color,
        Boolean collapsed) {
}
