# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

TenderReady is a hackathon prototype (team Fusion3) for Nova Scotia businesses. **`initial/idea.md` is the authoritative spec.** `README.md` has the demo script and configuration table.

## Commands

```bash
./scripts/run-backend.sh                 # exports .env, then mvn spring-boot:run (port 8090; Flyway migrates DB_SCHEMA, seeds demo data)
cd frontend && npm run dev               # Vite on 5173, proxies /api -> 8090
cd backend && mvn test                   # all tests; TenderImportIntegrationTest needs Docker (Testcontainers postgres:18-alpine)
cd backend && mvn test -Dtest=PartnerMatcherTest            # single test class
cd backend && mvn test -Dtest='!TenderImportIntegrationTest'  # no Docker
cd frontend && npx tsc -b && npm run build                  # type-check + build
./scripts/reset-demo.sh                  # removes and reseeds ONLY demo records (POST /api/demo/reset, localhost only)
```

Local DB: the existing PostgreSQL in `.env` (schema `tender`). Spring Boot does not read `.env` itself; the run script exports it. Never point tests at it, because tests use Testcontainers.

## Architecture

- **Backend** `backend/src/main/java/ca/fusion3/tenderready/`. One Spring Boot app, with packages `companies`, `tenders`, `assessment`, `matching`, `collaboration`, `ai`, `demo`, `common`. Business rules go in services or pure classes, not controllers.
- **Assessment pipeline:** a document bundle (`TenderService.bundle`) → company-independent extraction (`FixtureAnalysis` or `ClaudeAnalysisClient`, structured output into `Model.AiExtraction`) → `ExtractionValidator` (verbatim-excerpt check, hours must appear in the excerpt, permission without a verified passage becomes UNKNOWN) → cached in `document_extraction` per (tender, document version, mode) → human `RequirementCorrection`s applied → **`AssessmentEngine` (pure, deterministic)** evaluates against `CompanyFacts`. The AI never decides hours or qualifications.
- **Matching** (`PartnerMatcher`, pure) re-evaluates the latest assessment against the *current* profile, then shortlists opted-in, confirmed companies by declared hours in the gap's exact location.
- **Collaboration:** `InvitationRules` (pure) holds transitions and authorization. A combined reassessment (`AssessmentService.reassessCombined`) also creates a fresh baseline for the before/after comparison.
- **Fixture binding:** `backend/src/main/resources/demo/fixture-extraction.json` is keyed to the SHA-256 of `demo-tender-document.txt`. If you edit the document, recompute `documentSha256` and keep every `sourceExcerpt` verbatim.
- **Frontend** `frontend/src/`: hash routing (`context.tsx` holds the router, active-company context and loaders), pages in `pages/`, and the design system in `styles.css` ("Chart Room": colour tokens on `:root`, a dark "Night watch" theme, signal flags in `components/Flag.tsx`, and the SVG map in `components/NovaScotiaChart.tsx`). Status is always shape plus text (`Buoy`/`StatusMark`), never colour alone.
- **CanadaBuys feed facts:** `referenceNumber` is the unique id; closing dates are fixed UTC-05:00; `regionsOfDelivery` is the work location (empty means unknown); attachments are comma-separated URLs.

## Rules that are easy to violate

- **Demo identity:** the active company comes from the `X-Demo-Company-Id` header, validated against seeded IDs. Enforce ownership on the server: only the sender can invite or withdraw, and only the recipient can respond. The UI must say `Demo mode: simulated company access`, and this must never be called real security.
- **Data provenance:** keep live CanadaBuys data, the fictional demo tender/companies, and fixture AI output visibly separate (`isDemo` flags, `Demonstration analysis` label). Label the feed `CanadaBuys: federal opportunities`, not a Nova Scotia feed.
- **CSV import:** parse by header names with a real CSV library, because fields can be quoted and span multiple lines. Deduplicate on the source identifier, never the title. If the feed fails, keep existing records. A notice missing from a later import is not cancelled. Never fetch the feed on UI render. Imports happen only through manual refresh or the optional schedule (`TENDER_IMPORT_SCHEDULE_ENABLED`).
- **Delivery location:** work location is separate from the buyer's address. When it's unknown, display "unknown". Don't drop the tender or assign a location.
- **Assessment findings:** each finding is `MATCH | GAP | MISSING_EVIDENCE | UNKNOWN`. An empty profile field means `unknown`, never a failed qualification. A missing certificate is `MISSING_EVIDENCE`, not `GAP`. Compare capacity hours deterministically, by location, from the current profile, even in fixture mode. Never add hours across locations to declare eligibility.
- **Partnership permission:** `PERMITTED_WITH_CONDITIONS | PROHIBITED | UNKNOWN`, with the cited passage. Subcontracting is not a joint venture. Don't pool credentials (experience, insurance, certifications) across partners.
- **AI output:** schema-validated JSON. Verify that quoted excerpts appear in the source text, and flag them as unsupported if they don't. Treat tender text as untrusted input. If the document is incomplete, don't give a complete readiness verdict. Use fixtures only for the bundled fictional tender. Never substitute them silently for a live tender.
- **Document fetching:** use timeouts and size limits. Block localhost and private-network targets, including redirects to them. Never forward credentials.
- **Matching:** only opted-in companies stored in the platform can be candidates. Private evidence must not leak through partner search. Rankings are recommendations, not win probabilities.
- **Invitation states:** `INVITED → INTEREST_ACCEPTED → CONFIRMED_FOR_ASSESSMENT` (both companies' confirmations are stored separately and both are required), plus `DECLINED` and `WITHDRAWN`. Reject invalid transitions, self-invites, and duplicate active invites for the same tender and company pair. Invitations are created only by an explicit human `Approve and invite`, never by analysis.
- **Staleness:** assessments are bound to `profileRevision` and the document version. Changing a profile or partner marks them stale and offers `Reassess`. Editing a confirmed profile clears its confirmation.
- **No real-world side effects:** no external emails or invitations, and no bid submissions.

## Demo data and verification

The seed data and presenter journey are in idea.md §8: the fictional Halifax 60h + Truro 20h tender, Harbour Clean (A), and Fundy Clean (B). Required focused tests cover duplicate imports, capacity matching by location, opt-out filtering, and invitation authorization and state transitions. Also required: a local-only reset that deletes only demo records. When reporting work, say which integrations (live feed, live AI) actually ran and which parts used fixtures.
