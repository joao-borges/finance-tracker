package ca.joaoborges.finance.match;

import ca.joaoborges.finance.transaction.TransactionDto;
import ca.joaoborges.finance.transaction.TransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Transfer/refund matching: review surface (suggestions), manual matching, and a
 * backfill scan. Unmatch lives on the transaction controller.
 */
@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchingService matchingService;
    private final TransactionMapper transactionMapper;

    public record MatchSuggestionDto(Long id, MatchType type, TransactionDto legA, TransactionDto legB) {
    }

    public record ManualMatchRequest(Long aId, Long bId, MatchType type) {
    }

    @GetMapping("/suggestions")
    @Transactional(readOnly = true)
    public List<MatchSuggestionDto> suggestions() {
        return matchingService.suggestions().stream()
                .map(suggestion -> new MatchSuggestionDto(
                        suggestion.getId(),
                        suggestion.getType(),
                        transactionMapper.toDto(suggestion.getLegA()),
                        transactionMapper.toDto(suggestion.getLegB())))
                .toList();
    }

    @PostMapping("/suggestions/{id}/confirm")
    public void confirm(@PathVariable final Long id) {
        matchingService.confirm(id);
    }

    @PostMapping("/suggestions/{id}/dismiss")
    public void dismiss(@PathVariable final Long id) {
        matchingService.dismiss(id);
    }

    /** Manually pair two transactions. */
    @PostMapping
    public TransactionDto match(@RequestBody final ManualMatchRequest request) {
        return transactionMapper.toDto(matchingService.manualMatch(request.aId(), request.bId(), request.type()));
    }

    /** Scan existing transactions for matches (auto-applies high-confidence, suggests the rest). */
    @PostMapping("/scan")
    public Map<String, Integer> scan() {
        return Map.of("applied", matchingService.scan());
    }

}
