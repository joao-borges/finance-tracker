package ca.joaoborges.finance.category;

import lombok.Builder;

@Builder
public record CategoryDto(
        Long id,
        String name,
        String icon,
        Boolean income,
        Integer sortOrder,
        Boolean archived,
        Long groupId,
        String groupName) {
}
