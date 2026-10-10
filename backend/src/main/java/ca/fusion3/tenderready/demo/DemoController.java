package ca.fusion3.tenderready.demo;

import ca.fusion3.tenderready.common.ApiException;
import ca.fusion3.tenderready.common.AppProperties;
import ca.fusion3.tenderready.tenders.ImportRunRepository;
import ca.fusion3.tenderready.tenders.TenderDtos.ImportRunView;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {
    public record ResetRequest(String confirm) {}

    private final AppProperties props;
    private final ImportRunRepository runs;
    private final EntityManager em;
    private final DemoSeeder seeder;

    public DemoController(AppProperties props, ImportRunRepository runs, EntityManager em, DemoSeeder seeder) {
        this.props = props;
        this.runs = runs;
        this.em = em;
        this.seeder = seeder;
    }

    @GetMapping("/api/system/status")
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("demoMode", props.demoMode());
        m.put("demoModeNotice", "Demo mode: simulated company access. This switch is not authentication.");
        m.put("defaultAiMode", "live".equalsIgnoreCase(props.ai().mode()) ? "LIVE" : "FIXTURE");
        m.put("liveAiAvailable", props.ai().hasApiKey());
        m.put("model", props.ai().model());
        m.put("feedUrl", props.feed().url());
        m.put("importScheduleEnabled", props.feed().scheduleEnabled());
        m.put("lastImport", runs.findFirstByOrderByStartedAtDesc().map(ImportRunView::of).orElse(null));
        m.put("demoCompanyIds", List.of(DemoData.HARBOUR_CLEAN, DemoData.FUNDY_CLEAN));
        m.put("demoTenderId", DemoData.DEMO_TENDER);
        return m;
    }

    /**
     * Local-only: removes demo records (fictional companies, the fictional tender, and invitations / assessments /
     * corrections that involve them) from the dedicated schema, then reseeds. Imported notices are untouched.
     */
    @PostMapping("/api/demo/reset")
    @Transactional
    public Map<String, Object> reset(@RequestBody ResetRequest req, HttpServletRequest http) throws IOException {
        if (!props.demoMode()) throw ApiException.forbidden("Demo reset is disabled when DEMO_MODE=false.");
        if (!InetAddress.getByName(http.getRemoteAddr()).isLoopbackAddress()) {
            throw ApiException.forbidden("Demo reset is only allowed from localhost.");
        }
        if (req == null || !"RESET DEMO".equals(req.confirm())) {
            throw ApiException.badRequest("Send {\"confirm\": \"RESET DEMO\"} to reset demo records.");
        }
        String demoCompanies = "(select c.id from Company c where c.demo = true)";
        String demoTenders = "(select t.id from Tender t where t.demo = true)";
        int assessments = em.createQuery("delete from Assessment a where a.companyId in " + demoCompanies
                + " or a.tenderId in " + demoTenders).executeUpdate();
        int invitations = em.createQuery("delete from Invitation i where i.senderCompanyId in " + demoCompanies
                + " or i.recipientCompanyId in " + demoCompanies + " or i.tenderId in " + demoTenders).executeUpdate();
        int corrections = em.createQuery("delete from RequirementCorrection r where r.tenderId in " + demoTenders
                + " or r.correctedBy in " + demoCompanies).executeUpdate();
        em.createQuery("delete from DocumentExtraction d where d.tenderId in " + demoTenders).executeUpdate();
        em.createQuery("delete from TenderDocument d where d.demo = true").executeUpdate();
        int tenders = em.createQuery("delete from Tender t where t.demo = true").executeUpdate();
        int companies = em.createQuery("delete from Company c where c.demo = true").executeUpdate();
        em.flush();
        em.clear();
        seeder.seed();
        return Map.of("removed", Map.of("assessments", assessments, "invitations", invitations,
                "corrections", corrections, "tenders", tenders, "companies", companies), "reseeded", true);
    }
}
