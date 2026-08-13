package ca.joaoborges.finance.tag;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Resolves tag names to {@link Tag} rows, creating missing ones on the fly —
 * tagging a transaction with a new name is how tags come into existence.
 * Matching is case-insensitive so "Trip" and "trip" stay one tag.
 */
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    @Transactional
    public Set<Tag> resolve(final Collection<String> names) {
        final Set<Tag> tags = new LinkedHashSet<>();
        if (names == null) {
            return tags;
        }
        for (final String raw : names) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            final String name = raw.trim();
            tags.add(tagRepository.findByNameIgnoreCase(name)
                    .orElseGet(() -> tagRepository.save(Tag.builder().name(name).build())));
        }
        return tags;
    }

}
