package ca.fusion3.tenderready.matching;

import ca.fusion3.tenderready.companies.ActiveCompanyResolver;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MatchingController {
    private final MatchingService service;
    private final ActiveCompanyResolver resolver;

    public MatchingController(MatchingService service, ActiveCompanyResolver resolver) {
        this.service = service;
        this.resolver = resolver;
    }

    @GetMapping("/api/tenders/{tenderId}/partner-recommendations")
    public MatchingService.RecommendationsView recommendations(
            @PathVariable UUID tenderId, @RequestParam(defaultValue = "false") boolean aiExplanation,
            @RequestHeader(name = ActiveCompanyResolver.HEADER, required = false) String active) {
        return service.recommend(resolver.require(active), tenderId, aiExplanation);
    }
}
