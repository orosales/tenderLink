package ca.fusion3.tenderready.companies;

import ca.fusion3.tenderready.common.ApiException;
import ca.fusion3.tenderready.common.AppProperties;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves the active company from the {@code X-Demo-Company-Id} header.
 * DEMO ONLY: this is simulated company access for a presentation, not authentication.
 * Disable DEMO_MODE before any deployment that serves real customers.
 */
@Component
public class ActiveCompanyResolver {
    public static final String HEADER = "X-Demo-Company-Id";

    private final CompanyRepository companies;
    private final AppProperties props;

    public ActiveCompanyResolver(CompanyRepository companies, AppProperties props) {
        this.companies = companies;
        this.props = props;
    }

    public Company require(String headerValue) {
        if (!props.demoMode()) {
            throw ApiException.forbidden("Demo company switching is disabled (DEMO_MODE=false) and no real authentication exists.");
        }
        if (headerValue == null || headerValue.isBlank()) {
            throw ApiException.forbidden("Select an active demo company first (" + HEADER + " header missing).");
        }
        UUID id;
        try {
            id = UUID.fromString(headerValue.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.forbidden("Invalid demo company id.");
        }
        return companies.findById(id)
                .filter(Company::isDemo)
                .orElseThrow(() -> ApiException.forbidden("Only seeded demo companies can be selected in demo mode."));
    }
}
