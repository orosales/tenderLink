package ca.fusion3.tenderready.companies;

import ca.fusion3.tenderready.companies.CompanyDtos.CompanySummary;
import ca.fusion3.tenderready.companies.CompanyDtos.CompanyView;
import ca.fusion3.tenderready.companies.CompanyDtos.UpdateCompanyRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {
    private final CompanyRepository companies;
    private final CompanyService service;
    private final ActiveCompanyResolver resolver;

    public CompanyController(CompanyRepository companies, CompanyService service, ActiveCompanyResolver resolver) {
        this.companies = companies;
        this.service = service;
        this.resolver = resolver;
    }

    /** Summaries for the demo switcher. Exposes no profile detail. */
    @GetMapping
    public List<CompanySummary> list() {
        return companies.findAllByOrderByNameAsc().stream()
                .map(c -> new CompanySummary(c.getId(), c.getName(), c.isOpenToPartnerships(), c.isConfirmed(),
                        c.isDemo(), c.getServiceAreas()))
                .toList();
    }

    @GetMapping("/{id}")
    public Object get(@PathVariable UUID id,
                      @RequestHeader(name = ActiveCompanyResolver.HEADER, required = false) String activeId) {
        Company active = resolver.require(activeId);
        Company c = service.get(id);
        if (active.getId().equals(id)) return CompanyView.of(c);
        return CompanyDtos.CompanyPublicView.of(c);
    }

    @PutMapping("/{id}")
    public CompanyView update(@PathVariable UUID id, @Valid @RequestBody UpdateCompanyRequest req,
                              @RequestHeader(name = ActiveCompanyResolver.HEADER, required = false) String activeId) {
        return CompanyView.of(service.update(resolver.require(activeId), id, req));
    }

    @PostMapping("/{id}/confirm")
    public CompanyView confirm(@PathVariable UUID id,
                               @RequestHeader(name = ActiveCompanyResolver.HEADER, required = false) String activeId) {
        return CompanyView.of(service.confirm(resolver.require(activeId), id));
    }
}
