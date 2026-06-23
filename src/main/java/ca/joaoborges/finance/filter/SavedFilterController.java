package ca.joaoborges.finance.filter;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/saved-filters")
@RequiredArgsConstructor
public class SavedFilterController {

    private final SavedFilterRepository savedFilterRepository;
    private final SavedFilterMapper savedFilterMapper;

    @GetMapping
    public List<SavedFilterDto> list() {
        return savedFilterRepository.findAllByOrderByNameAsc().stream().map(savedFilterMapper::toDto).toList();
    }

    @PostMapping
    @Transactional
    public SavedFilterDto create(@RequestBody final SavedFilterDto dto) {
        if (!StringUtils.hasText(dto.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        return savedFilterMapper.toDto(savedFilterRepository.save(savedFilterMapper.toEntity(dto)));
    }

    @PatchMapping("/{id}")
    @Transactional
    public SavedFilterDto update(@PathVariable final Long id, @RequestBody final SavedFilterDto dto) {
        final SavedFilter filter = savedFilterRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved filter not found"));
        savedFilterMapper.update(filter, dto);
        return savedFilterMapper.toDto(savedFilterRepository.save(filter));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable final Long id) {
        if (!savedFilterRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved filter not found");
        }
        savedFilterRepository.deleteById(id);
    }

}
