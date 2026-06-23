package ca.joaoborges.finance.csv;

import ca.joaoborges.finance.common.SourceType;
import ca.joaoborges.finance.ingest.ImportRunDto;
import ca.joaoborges.finance.ingest.ImportRunMapper;
import ca.joaoborges.finance.ingest.ImportRunRepository;
import ca.joaoborges.finance.ingest.IngestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/imports")
@RequiredArgsConstructor
public class CsvImportController {

    private final IngestService ingestService;
    private final ImportRunRepository importRunRepository;
    private final ImportRunMapper importRunMapper;

    @GetMapping
    @Transactional(readOnly = true)
    public List<ImportRunDto> history() {
        return importRunRepository.findAllByOrderByStartedAtDesc().stream().map(importRunMapper::toDto).toList();
    }

    /** Upload a CSV in one of the supported {@link CsvFormat}s and ingest it. */
    @PostMapping("/csv")
    public ImportRunDto importCsv(@RequestParam("file") final MultipartFile file,
                                  @RequestParam(name = "format", defaultValue = "SIMPLE") final CsvFormat format) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
        }
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            final List<ParsedTransaction> rows = parse(format, reader);
            return importRunMapper.toDto(ingestService.ingest(rows, SourceType.CSV, file.getOriginalFilename()));
        } catch (final IOException wrapped) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read upload", wrapped);
        } catch (final IllegalArgumentException | UncheckedIOException badData) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, badData.getMessage(), badData);
        }
    }

    private List<ParsedTransaction> parse(final CsvFormat format, final Reader reader) {
        return switch (format) {
            case SIMPLE -> SimpleCsvParser.parse(reader);
            case AMEX -> AmexCsvParser.parse(reader);
            case RBC -> RbcCsvParser.parse(reader);
            case PC_FINANCIAL -> PcFinancialCsvParser.parse(reader);
        };
    }

}
