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

/**
 * PC Financial "report" export: {@code Description, Type, Card Holder Name, Date,
 * Time, Amount}. Amount is already signed (negative = outflow). There is no
 * account column, so everything lands under a single "PC Financial" account.
 */
public final class PcFinancialCsvParser {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final String ACCOUNT = "PC Financial";

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
                final BigDecimal amount = new BigDecimal(rawAmount.replace(",", "").trim());
                final LocalDate date = LocalDate.parse(rawDate.trim(), DATE);
                final String merchant = record.get("Description").trim();
                rows.add(new ParsedTransaction(ACCOUNT, merchant, amount, date.atTime(12, 0).atOffset(ZoneOffset.UTC).toInstant()));
            }
        } catch (final IOException wrapped) {
            throw new UncheckedIOException("Failed to read PC Financial CSV", wrapped);
        }
        return rows;
    }

    private PcFinancialCsvParser() {
    }

}
