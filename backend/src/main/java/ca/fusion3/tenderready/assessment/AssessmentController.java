package ca.fusion3.tenderready.assessment;

import ca.fusion3.tenderready.assessment.AssessmentDtos.AssessmentView;
import ca.fusion3.tenderready.companies.ActiveCompanyResolver;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AssessmentController {
    public record AssessRequest(Model.Mode mode) {}

    private final AssessmentService service;
    private final ActiveCompanyResolver resolver;

    public AssessmentController(AssessmentService service, ActiveCompanyResolver resolver) {
        this.service = service;
        this.resolver = resolver;
    }

    /** Assess the active company against the tender, with an explicit LIVE or FIXTURE mode. */
    @PostMapping("/tenders/{tenderId}/assessments")
    public AssessmentView assess(@PathVariable UUID tenderId, @RequestBody(required = false) AssessRequest req,
                                 @RequestHeader(name = ActiveCompanyResolver.HEADER, required = false) String active) {
        return service.assess(resolver.require(active), tenderId, req == null ? null : req.mode());
    }

    @GetMapping("/tenders/{tenderId}/assessments/latest")
    public ResponseEntity<AssessmentView> latest(@PathVariable UUID tenderId,
                                                 @RequestHeader(name = ActiveCompanyResolver.HEADER, required = false) String active) {
        AssessmentView v = service.latest(resolver.require(active), tenderId);
        return v == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(v);
    }

    @GetMapping("/assessments/{id}")
    public AssessmentView get(@PathVariable UUID id,
                              @RequestHeader(name = ActiveCompanyResolver.HEADER, required = false) String active) {
        return service.get(resolver.require(active), id);
    }

    @PatchMapping("/assessments/{id}/requirements/{requirementId}")
    public AssessmentView correct(@PathVariable UUID id, @PathVariable String requirementId,
                                  @RequestBody RequirementCorrection.Payload payload,
                                  @RequestHeader(name = ActiveCompanyResolver.HEADER, required = false) String active) {
        return service.correct(resolver.require(active), id, requirementId, payload);
    }
}
