package ca.joaoborges.finance.csv;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Amex Canada "activity" export. Columns vary (a "Flexible" column may be
 * present), so fields are read by header name. Amounts are POSITIVE for spend,
 * so the sign is flipped to the app's convention (negative = outflow). Account
 * is derived from the card's "Account #".
 */
public final class AmexCsvParser {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    public static List<ParsedTransaction> parse(final Reader reader) {
        final CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get();
        final List<ParsedTransaction> rows = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(reader, format)) {
            for (final CSVRecord record : parser) {
                final String rawAmount = record.get("Amount");
                final String rawDate = record.get("Date");
                if (!StringUtils.hasText(rawAmount) || !StringUtils.hasText(rawDate)) {
                    continue;
                }
                final BigDecimal amount = new BigDecimal(rawAmount.replace(",", "").trim()).negate();
                final LocalDate date = LocalDate.parse(rawDate.trim(), DATE);
                final String merchant = collapse(record.get("Description"));
                final String account = "Amex " + record.get("Account #").replaceAll("[^0-9]", "");
                rows.add(new ParsedTransaction(account.trim(), merchant, amount, date.atTime(12, 0).atOffset(ZoneOffset.UTC).toInstant()));
            }
        } catch (final IOException wrapped) {
            throw new UncheckedIOException("Failed to read Amex CSV", wrapped);
        }
        return rows;
    }

    private static String collapse(final String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private AmexCsvParser() {
    }

}
