package ca.joaoborges.finance.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Stores a small list of strings as one comma-separated column — the same
 * shape as {@link LongListConverter}, for saved-filter tag names. Values
 * containing a comma are not supported (tag names are single words in
 * practice); blanks are dropped.
 */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(final List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join(",", values);
    }

    @Override
    public List<String> convertToEntityAttribute(final String column) {
        final List<String> values = new ArrayList<>();
        if (column == null || column.isBlank()) {
            return values;
        }
        Arrays.stream(column.split(",")).map(String::trim).filter(value -> !value.isEmpty()).forEach(values::add);
        return values;
    }

}
