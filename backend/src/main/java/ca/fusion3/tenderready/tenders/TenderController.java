package ca.fusion3.tenderready.tenders;

import ca.fusion3.tenderready.tenders.TenderDtos.ImportRunView;
import ca.fusion3.tenderready.tenders.TenderDtos.TenderDetail;
import ca.fusion3.tenderready.tenders.TenderDtos.TenderList;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenders")
public class TenderController {
    private final TenderService service;
    private final TenderImportService importer;

    public TenderController(TenderService service, TenderImportService importer) {
        this.service = service;
        this.importer = importer;
    }

    /** Manual "Refresh public tenders". Failures are reported in the run record, never thrown away silently. */
    @PostMapping("/import")
    public ImportRunView importFeed() {
        return ImportRunView.of(importer.importFromFeed("MANUAL"));
    }

    @GetMapping
    public TenderList list(@RequestParam(required = false) String service,
                           @RequestParam(required = false) String location,
                           @RequestParam(defaultValue = "true") boolean includeUnknownLocation,
                           @RequestParam(defaultValue = "100") int limit) {
        return this.service.list(service, location, includeUnknownLocation, limit);
    }

    @GetMapping("/{id}")
    public TenderDetail detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PostMapping("/{id}/documents/fetch")
    public TenderDetail fetchDocuments(@PathVariable UUID id) {
        return service.fetchDocuments(id);
    }
}
