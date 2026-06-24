package ca.joaoborges.finance.category;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record CategoryDto(
        Long id,
        String name,
        String icon,
        Boolean income,
        Integer sortOrder,
        Boolean archived,
        BigDecimal alertThreshold,
        Long groupId,
        String groupName) {
}
