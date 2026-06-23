package ca.joaoborges.finance.csv;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleCsvParserTest {

    @Test
    void parsesSignedAmountsAndMapsNameToMerchant() {
        final String csv = """
                account,name,value
                Chequing,Tim Hortons,-4.50
                Chequing,Paycheck,+2500.00
                """;

        final List<ParsedTransaction> rows = SimpleCsvParser.parse(new StringReader(csv));

        assertEquals(2, rows.size());
        assertEquals("Chequing", rows.get(0).accountName());
        assertEquals("Tim Hortons", rows.get(0).merchantName());
        assertEquals(0, new BigDecimal("-4.50").compareTo(rows.get(0).amount()));
        assertNull(rows.get(0).postedAt());
    }

    @Test
    void honoursQuotedCommasInMerchant() {
        final String csv = "account,name,value\nVisa,\"AMZN MKTP, CA\",-19.99\n";

        final List<ParsedTransaction> rows = SimpleCsvParser.parse(new StringReader(csv));

        assertEquals("AMZN MKTP, CA", rows.get(0).merchantName());
    }

    @Test
    void rejectsMalformedAmount() {
        final String csv = "account,name,value\nVisa,Netflix,not-a-number\n";

        final IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> SimpleCsvParser.parse(new StringReader(csv)));
        assertTrue(error.getMessage().contains("Invalid amount"));
    }

}
