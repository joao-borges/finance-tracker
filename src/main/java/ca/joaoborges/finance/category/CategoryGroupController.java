package ca.joaoborges.finance.category;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/category-groups")
@RequiredArgsConstructor
public class CategoryGroupController {

    private final CategoryGroupRepository categoryGroupRepository;
    private final CategoryGroupMapper categoryGroupMapper;

    @GetMapping
    public List<CategoryGroupDto> list() {
        return categoryGroupRepository.findAllByOrderByNameAsc().stream()
                .map(categoryGroupMapper::toDto)
                .toList();
    }

    @PostMapping
    @Transactional
    public CategoryGroupDto create(@RequestBody final CategoryGroupDto dto) {
        if (!StringUtils.hasText(dto.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        return categoryGroupMapper.toDto(categoryGroupRepository.save(categoryGroupMapper.toEntity(dto)));
    }

    @PatchMapping("/{id}")
    @Transactional
    public CategoryGroupDto update(@PathVariable final Long id, @RequestBody final CategoryGroupDto dto) {
        final CategoryGroup group = categoryGroupRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category group not found"));
        categoryGroupMapper.update(group, dto);
        return categoryGroupMapper.toDto(categoryGroupRepository.save(group));
    }

}
