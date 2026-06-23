package ca.joaoborges.finance.csv;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Bootstrap {@code account,name,value} parser. No date column, so
 * {@code postedAt} is left null (ingest defaults it). Pure and DB-free.
 */
public final class SimpleCsvParser {

    public static List<ParsedTransaction> parse(final Reader reader) {
        final CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get();
        final List<ParsedTransaction> rows = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(reader, format)) {
            for (final CSVRecord record : parser) {
                final String account = record.get("account");
                final String name = record.get("name");
                final String value = record.get("value");
                if (!StringUtils.hasText(account) || !StringUtils.hasText(name) || !StringUtils.hasText(value)) {
                    throw new IllegalArgumentException("Blank field on CSV line " + record.getRecordNumber());
                }
                rows.add(new ParsedTransaction(account.trim(), name.trim(), parseAmount(value, record.getRecordNumber()), null));
            }
        } catch (final IOException wrapped) {
            throw new UncheckedIOException("Failed to read CSV", wrapped);
        }
        return rows;
    }

    private static BigDecimal parseAmount(final String value, final long line) {
        try {
            return new BigDecimal(value.trim());
        } catch (final NumberFormatException wrapped) {
            throw new IllegalArgumentException("Invalid amount '" + value + "' on CSV line " + line, wrapped);
        }
    }

    private SimpleCsvParser() {
    }

}
