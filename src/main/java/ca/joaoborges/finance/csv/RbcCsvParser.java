package ca.joaoborges.finance.csv;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

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
 * RBC "download-transactions" export. The header has a UTF-8 BOM, so columns are
 * read by index (header row skipped) to avoid it. Layout:
 * {@code Account Type, Account Number, Transaction Date, Cheque Number,
 * Description 1, Description 2, CAD$, USD$}. CAD$ is already signed
 * (negative = outflow); rows without a CAD amount are skipped.
 */
public final class RbcCsvParser {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M/d/yyyy");

    public static List<ParsedTransaction> parse(final Reader reader) {
        final CSVFormat format = CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(true).get();
        final List<ParsedTransaction> rows = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(reader, format)) {
            boolean header = true;
            for (final CSVRecord record : parser) {
                if (header) {
                    header = false;
                    continue;
                }
                if (record.size() < 7) {
                    continue;
                }
                final String cad = record.get(6).trim();
                if (cad.isEmpty()) {
                    continue;
                }
                final BigDecimal amount = new BigDecimal(cad.replace(",", ""));
                final LocalDate date = LocalDate.parse(record.get(2).trim(), DATE);
                final String merchant = record.get(4).trim();
                final String account = (record.get(0).trim() + " " + record.get(1).trim()).trim();
                rows.add(new ParsedTransaction(account, merchant, amount, date.atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
        } catch (final IOException wrapped) {
            throw new UncheckedIOException("Failed to read RBC CSV", wrapped);
        }
        return rows;
    }

    private RbcCsvParser() {
    }

}
