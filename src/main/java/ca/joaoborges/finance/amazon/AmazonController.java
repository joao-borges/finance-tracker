package ca.joaoborges.finance.amazon;

import ca.joaoborges.finance.transaction.Transaction;
import ca.joaoborges.finance.transaction.TransactionDto;
import ca.joaoborges.finance.transaction.TransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Amazon order details: the browser extension's import, the per-transaction
 * "what did this charge buy" lookup, hand linking of charges, and the
 * extension download itself.
 */
@RestController
@RequestMapping("/api/amazon")
@RequiredArgsConstructor
public class AmazonController {

    private final AmazonService amazonService;
    private final AmazonOrderMapper amazonOrderMapper;
    private final TransactionMapper transactionMapper;
    private final ExtensionPackager extensionPackager;

    public record LinkRequest(Long transactionId) {
    }

    @PostMapping("/import")
    public AmazonImportResult importOrders(@RequestBody final AmazonImportRequest request) {
        return amazonService.importOrders(request);
    }

    @PostMapping("/rematch")
    public Map<String, Integer> rematch() {
        return Map.of("matched", amazonService.rematch());
    }

    @GetMapping("/orders")
    @Transactional(readOnly = true)
    public List<AmazonOrderDto> orders() {
        return amazonService.orders().stream().map(amazonOrderMapper::toDto).toList();
    }

    /** The orders behind one transaction (empty when it isn't a linked Amazon charge). */
    @GetMapping("/transactions/{id}")
    @Transactional(readOnly = true)
    public List<AmazonOrderDto> ordersForTransaction(@PathVariable final Long id) {
        return amazonService.ordersFor(id).stream().map(amazonOrderMapper::toDto).toList();
    }

    @GetMapping("/charges/{id}/candidates")
    @Transactional(readOnly = true)
    public List<TransactionDto> candidates(@PathVariable final Long id) {
        final List<Transaction> candidates = amazonService.candidates(id);
        final Map<Long, String> summaries = amazonService.summaries(candidates);
        return candidates.stream()
                .map(transaction -> transactionMapper.toDto(transaction).toBuilder()
                        .amazonSummary(summaries.get(transaction.getId()))
                        .build())
                .toList();
    }

    @PutMapping("/charges/{id}/transaction")
    @Transactional
    public AmazonOrderDto link(@PathVariable final Long id, @RequestBody final LinkRequest request) {
        if (request == null || request.transactionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "transactionId is required");
        }
        return amazonOrderMapper.toDto(amazonService.link(id, request.transactionId()));
    }

    @DeleteMapping("/charges/{id}/transaction")
    @Transactional
    public AmazonOrderDto unlink(@PathVariable final Long id) {
        return amazonOrderMapper.toDto(amazonService.unlink(id));
    }

    @GetMapping("/extension")
    public Map<String, String> extension() {
        return Map.of("version", extensionPackager.version());
    }

    /** The extension as a zip, wired to the origin the UI runs on (passed by the UI). */
    @GetMapping("/extension.zip")
    public ResponseEntity<byte[]> extensionZip(@RequestParam final String origin) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("finance-amazon-extension-" + extensionPackager.version() + ".zip")
                        .build()
                        .toString())
                .body(extensionPackager.zip(origin));
    }

}
