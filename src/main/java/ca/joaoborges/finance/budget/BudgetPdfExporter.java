package ca.joaoborges.finance.budget;

import ca.joaoborges.finance.transaction.Transaction;
import ca.joaoborges.finance.transaction.TransactionRepository;
import ca.joaoborges.finance.transaction.TransactionSpecs;
import lombok.RequiredArgsConstructor;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Renders a month's budget to PDF: a budget page (income + expense groups with
 * planned/actual/remaining) followed by the month's transactions. Text uses the
 * PDF base fonts, so category/merchant emojis are intentionally omitted (those
 * glyphs aren't in the base fonts).
 */
@Service
@RequiredArgsConstructor
public class BudgetPdfExporter {

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font HEADING = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font HEADER = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font CELL = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(Locale.CANADA);

    private final BudgetService budgetService;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public byte[] export(final String month) {
        final BudgetSummary summary = budgetService.summary(month);
        final YearMonth ym = YearMonth.parse(month);
        final String label = ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
        final List<Transaction> transactions = monthTransactions(ym);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph("Budget — " + label, TITLE));
            document.add(spacer());
            addSection(document, "Income", summary.income());
            for (final BudgetSummary.BudgetGroup group : summary.groups()) {
                addSection(document, group.groupName(), group.categories());
            }
            document.add(spacer());
            document.add(new Paragraph("Planned income: " + money(summary.plannedIncome())
                    + "    Planned expense: " + money(summary.plannedExpense())
                    + "    Left to budget: " + money(summary.leftToBudget()), CELL));

            document.newPage();
            document.add(new Paragraph("Transactions — " + label, TITLE));
            document.add(spacer());
            addTransactions(document, transactions);

            document.close();
        } catch (final DocumentException failure) {
            throw new IllegalStateException("Failed to build budget PDF", failure);
        }
        return out.toByteArray();
    }

    private List<Transaction> monthTransactions(final YearMonth ym) {
        final Instant start = ym.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        final Instant end = ym.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        final Specification<Transaction> spec = TransactionSpecs.visible()
                .and(TransactionSpecs.postedFrom(start))
                .and(TransactionSpecs.postedBefore(end));
        return transactionRepository.findAll(spec, Sort.by(Sort.Order.asc("postedAt"), Sort.Order.asc("id")));
    }

    private void addSection(final Document document, final String title, final List<BudgetSummary.BudgetLine> lines) throws DocumentException {
        document.add(new Paragraph(title, HEADING));
        final PdfPTable table = new PdfPTable(new float[]{4, 2, 2, 2});
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setSpacingAfter(10);
        table.addCell(header("Category"));
        table.addCell(headerRight("Planned"));
        table.addCell(headerRight("Actual"));
        table.addCell(headerRight("Remaining"));
        for (final BudgetSummary.BudgetLine line : lines) {
            table.addCell(text(line.name()));
            table.addCell(amount(line.planned()));
            table.addCell(amount(line.actual()));
            table.addCell(amount(line.remaining()));
        }
        document.add(table);
    }

    private void addTransactions(final Document document, final List<Transaction> transactions) throws DocumentException {
        final PdfPTable table = new PdfPTable(new float[]{2, 3, 4, 3, 2});
        table.setWidthPercentage(100);
        table.addCell(header("Date"));
        table.addCell(header("Account"));
        table.addCell(header("Merchant"));
        table.addCell(header("Category"));
        table.addCell(headerRight("Amount"));
        for (final Transaction transaction : transactions) {
            table.addCell(text(DAY.format(transaction.getPostedAt())));
            table.addCell(text(transaction.getAccount().getName()));
            table.addCell(text(transaction.getMerchant() != null
                    ? transaction.getMerchant().getName()
                    : transaction.getMerchantName()));
            table.addCell(text(transaction.getCategory() != null ? transaction.getCategory().getName() : "Uncategorized"));
            table.addCell(amount(transaction.getAmount()));
        }
        document.add(table);
    }

    private Paragraph spacer() {
        return new Paragraph(" ", CELL);
    }

    private String money(final BigDecimal value) {
        return MONEY.format(value == null ? BigDecimal.ZERO : value);
    }

    private PdfPCell header(final String value) {
        final PdfPCell cell = new PdfPCell(new Phrase(value, HEADER));
        cell.setPadding(4);
        return cell;
    }

    private PdfPCell headerRight(final String value) {
        final PdfPCell cell = header(value);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return cell;
    }

    private PdfPCell text(final String value) {
        final PdfPCell cell = new PdfPCell(new Phrase(value == null ? "" : value, CELL));
        cell.setPadding(4);
        return cell;
    }

    private PdfPCell amount(final BigDecimal value) {
        final PdfPCell cell = new PdfPCell(new Phrase(money(value), CELL));
        cell.setPadding(4);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return cell;
    }

}
