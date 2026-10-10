package ca.fusion3.tenderready.collaboration;

import ca.fusion3.tenderready.assessment.AssessmentDtos.AssessmentView;
import ca.fusion3.tenderready.assessment.AssessmentService;
import ca.fusion3.tenderready.collaboration.InvitationService.CreateInvitationRequest;
import ca.fusion3.tenderready.collaboration.InvitationService.InvitationView;
import ca.fusion3.tenderready.companies.ActiveCompanyResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invitations")
public class InvitationController {
    public record RespondRequest(@NotNull InvitationRules.Response response) {}

    private final InvitationService service;
    private final AssessmentService assessments;
    private final ActiveCompanyResolver resolver;

    public InvitationController(InvitationService service, AssessmentService assessments, ActiveCompanyResolver resolver) {
        this.service = service;
        this.assessments = assessments;
        this.resolver = resolver;
    }

    @PostMapping
    public InvitationView create(@Valid @RequestBody CreateInvitationRequest req,
                                 @RequestHeader(name = ActiveCompanyResolver.HEADER, required = false) String active) {
        return service.create(resolver.require(active), req);
    }

    @GetMapping
    public List<InvitationView> list(@RequestHeader(name = ActiveCompanyResolver.HEADER, required = false) String active) {
        return service.list(resolver.require(active));
    }

    @GetMapping("/{id}")
    public InvitationView get(@PathVariable UUID id,
                              @RequestHeader(name = ActiveCompanyResolver.HEADER, required = false) String active) {
        return service.get(resolver.require(active), id);
    }

    @PostMapping("/{id}/respond")
    public InvitationView respond(@PathVariable UUID id, @Valid @RequestBody RespondRequest req,
                                  @RequestHeader(name = ActiveCompanyResolver.HEADER, required = false) String active) {
        return service.respond(resolver.require(active), id, req.response());
    }

    @PostMapping("/{id}/confirm-contribution")
    public InvitationView confirm(@PathVariable UUID id,
                                  @RequestHeader(name = ActiveCompanyResolver.HEADER, required = false) String active) {
        return service.confirm(resolver.require(active), id);
    }

    @PostMapping("/{id}/withdraw")
    public InvitationView withdraw(@PathVariable UUID id,
                                   @RequestHeader(name = ActiveCompanyResolver.HEADER, required = false) String active) {
        return service.withdraw(resolver.require(active), id);
    }

    @PostMapping("/{id}/reassess")
    public AssessmentView reassess(@PathVariable UUID id,
                                   @RequestHeader(name = ActiveCompanyResolver.HEADER, required = false) String active) {
        return assessments.reassessCombined(resolver.require(active), id);
    }
}
