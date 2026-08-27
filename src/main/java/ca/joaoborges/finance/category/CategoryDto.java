package ca.joaoborges.finance.category;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record CategoryDto(
        Long id,
        String name,
        String icon,
        Boolean income,
        String oneTimeMonth,
        Integer sortOrder,
        Boolean archived,
        Boolean hidden,
        BigDecimal alertThreshold,
        Long groupId,
        String groupName) {
}
