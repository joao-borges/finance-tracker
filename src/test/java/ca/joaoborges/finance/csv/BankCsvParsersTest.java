package ca.joaoborges.finance.csv;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BankCsvParsersTest {

    @Test
    void amexFlipsSignAndDerivesAccount() {
        // Spend is positive in Amex exports; the app stores outflow as negative.
        final String csv = """
                Date,Description,Card Member,Account #,Amount,Merchant
                21 Jun 2026,QUALITY MOVING          Vancouver,JOAO,-01004,20.00,QUALITY MOVING
                10 Jun 2026,PAYMENT RECEIVED,JOAO,-01004,-50.00,PAYMENT
                """;

        final List<ParsedTransaction> rows = AmexCsvParser.parse(new StringReader(csv));

        assertEquals(2, rows.size());
        assertEquals("Amex 01004", rows.get(0).accountName());
        assertEquals("QUALITY MOVING Vancouver", rows.get(0).merchantName());
        assertEquals(0, new BigDecimal("-20.00").compareTo(rows.get(0).amount()));
        assertEquals(0, new BigDecimal("50.00").compareTo(rows.get(1).amount()));
        assertNotNull(rows.get(0).postedAt());
    }

    @Test
    void rbcUsesSignedCadAndSkipsBlankAndBom() {
        final String csv = "﻿Account Type,Account Number,Transaction Date,Cheque Number,Description 1,Description 2,CAD$,USD$\n"
                + "Visa,4514012400253551,1/4/2026,,Funko Everett,98.91 USD @ 1.40,-139.40,\n"
                + "Chequing,03489-5086459,1/2/2026,,MONTHLY FEE,,-16.95,\n"
                + "Chequing,03489-5086459,1/3/2026,,NO AMOUNT,,,\n";

        final List<ParsedTransaction> rows = RbcCsvParser.parse(new StringReader(csv));

        assertEquals(2, rows.size());
        assertEquals("Visa 4514012400253551", rows.get(0).accountName());
        assertEquals("Funko Everett", rows.get(0).merchantName());
        assertEquals(0, new BigDecimal("-139.40").compareTo(rows.get(0).amount()));
    }

    @Test
    void pcFinancialUsesSingleAccountAndSignedAmount() {
        final String csv = "\"Description\",\"Type\",\"Card Holder Name\",\"Date\",\"Time\",\"Amount\"\n"
                + "\"HAKAM'S YIG #1869\",\"PURCHASE\",\"JOAO\",\"06/17/2026\",\"04:10 AM\",\"-4.42\"\n";

        final List<ParsedTransaction> rows = PcFinancialCsvParser.parse(new StringReader(csv));

        assertEquals(1, rows.size());
        assertEquals("PC Financial", rows.get(0).accountName());
        assertEquals("HAKAM'S YIG #1869", rows.get(0).merchantName());
        assertEquals(0, new BigDecimal("-4.42").compareTo(rows.get(0).amount()));
    }

}
