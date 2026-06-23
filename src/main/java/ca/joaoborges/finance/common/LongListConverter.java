package ca.joaoborges.finance.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores a list of ids as a comma-separated string in a single column. Used for
 * saved-filter criteria (account/merchant/category id lists) — plain ids, no
 * FKs, so a saved filter survives deletion of the referenced rows.
 */
@Converter
public class LongListConverter implements AttributeConverter<List<Long>, String> {

    @Override
    public String convertToDatabaseColumn(final List<Long> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        final StringBuilder joined = new StringBuilder();
        for (final Long value : values) {
            if (!joined.isEmpty()) {
                joined.append(',');
            }
            joined.append(value);
        }
        return joined.toString();
    }

    @Override
    public List<Long> convertToEntityAttribute(final String column) {
        final List<Long> values = new ArrayList<>();
        if (column == null || column.isBlank()) {
            return values;
        }
        for (final String token : column.split(",")) {
            final String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                values.add(Long.valueOf(trimmed));
            }
        }
        return values;
    }

}
