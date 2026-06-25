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
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final CategoryGroupRepository categoryGroupRepository;
    private final CategoryMapper categoryMapper;

    @GetMapping
    @Transactional(readOnly = true)
    public List<CategoryDto> list() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(categoryMapper::toDto)
                .toList();
    }

    @PostMapping
    @Transactional
    public CategoryDto create(@RequestBody final CategoryDto dto) {
        if (!StringUtils.hasText(dto.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        final Category category = categoryMapper.toEntity(dto);
        category.setGroup(resolveGroup(dto.groupId()));
        return categoryMapper.toDto(categoryRepository.save(category));
    }

    @PatchMapping("/{id}")
    @Transactional
    public CategoryDto update(@PathVariable final Long id, @RequestBody final CategoryDto dto) {
        final Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        categoryMapper.update(category, dto);
        if (dto.groupId() != null) {
            category.setGroup(resolveGroup(dto.groupId()));
        }
        return categoryMapper.toDto(categoryRepository.save(category));
    }

    private CategoryGroup resolveGroup(final Long groupId) {
        if (groupId == null) {
            return null;
        }
        return categoryGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category group " + groupId));
    }

}
