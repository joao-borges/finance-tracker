package ca.joaoborges.finance.tag;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Existing tag names, for the transactions-page filter and the tag editor's
 * suggestions. Tags are created implicitly by tagging a transaction, so there
 * is no create/update surface here.
 */
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagRepository tagRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public List<String> list() {
        return tagRepository.findAllByOrderByNameAsc().stream().map(Tag::getName).toList();
    }

}
