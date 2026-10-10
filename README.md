# TenderReady (Fusion3 hackathon prototype)

TenderReady helps Nova Scotia businesses find public tenders and see which requirements they can't meet alone. It also finds complementary businesses that could fill those gaps. People approve every invitation and confirm every proposed collaboration. The app never claims a team will win, and it never says a team is automatically eligible.

This is a **demonstration prototype**: company switching is simulated, not authentication.

```
frontend/  React + TypeScript + Vite  ("Chart Room" UI, port 5173, proxies /api)
backend/   Java 21 + Spring Boot 3.5 + Maven, JPA + Flyway, PostgreSQL (port 8090)
initial/idea.md   the implementation brief
```

## Configuration

Copy `.env.example` to `.env` and fill it in. **Spring Boot does not read `.env` itself.** `scripts/run-backend.sh` exports the file before it starts Maven. You can also export the same variables in your shell or IDE run configuration.

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Existing PostgreSQL. A Prisma-style `postgresql://u:p@host:port/db?schema=x` becomes `DB_URL=jdbc:postgresql://host:port/db` and `DB_SCHEMA=x` |
| `DB_SCHEMA` | Dedicated schema (default `tender`). Flyway creates and migrates it and touches nothing else |
| `ANTHROPIC_API_KEY` | Backend only, needed for live AI. Leave it unset to use only the demonstration analysis |
| `ANTHROPIC_MODEL` | Default `claude-opus-5-5` |
| `AI_MODE` | `fixture` (default) or `live`. This sets the default mode; the UI can choose per assessment |
| `DEMO_MODE` | `true` enables the simulated company switch. **Set it to `false` before any real deployment** |
| `TENDER_FEED_URL` | CanadaBuys open tender notices CSV |
| `TENDER_IMPORT_SCHEDULE_ENABLED` | `true` turns on the daily import (cron `TENDER_IMPORT_CRON`, default 06:30) |
| `REVIEWED_DOCUMENT_URLS` | Optional `REF=https://...` pairs: a reviewed public document for a real notice |

The frontend talks to `/api`. In development, Vite proxies `/api` to `http://localhost:8090` (override with `VITE_API_PROXY_TARGET`). If you serve the built frontend from another origin, set `VITE_API_BASE_URL` at build time and add that origin to `CORS_ORIGINS`. The AI key never goes to the frontend.

## Run

```bash
# backend (migrates the schema and seeds the fictional demo records)
./scripts/run-backend.sh

# frontend
cd frontend && npm install && npm run dev      # http://localhost:5173
```

## Test

```bash
cd backend && mvn test                                   # all tests (the import test starts a throwaway postgres:18-alpine via Testcontainers; needs Docker)
cd backend && mvn test -Dtest=AssessmentEngineTest       # a single test class
cd backend && mvn test -Dtest='!TenderImportIntegrationTest'   # unit tests only, no Docker
cd frontend && npm run build                             # type-check + production build
```

Focused tests cover duplicate imports, CSV parsing, capacity matching by location, opt-out and unconfirmed filtering, invitation authorization and state transitions, and the AI guards (invented hours and unsupported quotes are rejected).

## Demonstration script

All organizations in the demo are fictional.

1. Hoist **Harbour Clean** (signal flag Alfa) and show its confirmed profile: 60 h/week in Halifax, with its CleanSafe qualification declared but no evidence yet.
2. **Tenders**, then **Refresh public tenders**. This runs a real CanadaBuys import and reports the counts. Open the **fictional tender**.
3. **Run gap assessment** (Demonstration, or Live AI if a key is set). The chart shows Halifax 60/60 covered and two gaps: **Truro 0/20** and **Kentville 0/15**. CleanSafe shows **MISSING EVIDENCE** (not GAP). The subcontracting clause, permitted with conditions, is cited from Section 4.1.
4. **Partner recommendations**: Fundy Clean appears because its confirmed profile declares 20 h/week in Truro.
5. **Approve and invite…**, review the contribution and roles, then send. The invitation stays in-app only.
6. Hoist **Fundy Clean** (flag Bravo), then **Accept interest** and **Confirm contribution**.
7. Hoist Harbour Clean again and **Confirm contribution**. The state becomes `CONFIRMED_FOR_ASSESSMENT`.
8. **Reassess tender**: before/after charts show Truro covered *conditionally*, while Kentville stays a gap because no stored partner declares capacity there. Harbour's missing evidence, the liability insurance (unknown) and the buyer's approval of the named subcontractor stay open.

To show that recommendations react to profile changes: as Fundy, remove the Truro capacity or untick "Open to partnerships". The recommendation disappears, and the combined assessment is flagged stale.

**Reset**: `./scripts/reset-demo.sh`, or "Reset demo data…" on the profile page (localhost only). This removes only the fictional companies, the fictional tender and the invitations, assessments and corrections that involve them, then reseeds. Imported notices are untouched.

## Design notes

- **Provenance is always visible.** Every record carries a stamp: *Live · CanadaBuys federal* for imported notices, *Fictional demo* for seeded records, and *Demonstration analysis* or *Live AI analysis* for assessments. Fixture analysis is bound to the SHA-256 of the bundled document and the fictional tender, so it can't be applied to anything else.
- **The AI interprets, code decides.** Claude extracts requirements into a schema-validated structure. Excerpts must occur verbatim in the document, or they are flagged as unsupported. Hours must appear in the quoted excerpt, or they are discarded. Capacity is compared deterministically per location, and qualifications are evaluated per company and never pooled. Extractions are cached per document version, so reassessment doesn't call the AI again.
- **Honest incompleteness.** If the full solicitation can't be retrieved (missing attachment, sign-in wall, scanned PDF), the assessment says *Document unavailable / assessment incomplete* and gives no verdict.
- **Feed handling.** The CSV is parsed by header with Apache Commons CSV (quoted multi-line fields, BOM). Closing times use the fixed UTC-05:00 basis from the data dictionary. Notices are deduplicated on the reference number. A failed import keeps existing records, and a notice missing from a later import isn't cancelled. Downloads are bounded by both time and size.
- **Document fetching** allows http(s) only. It blocks private and loopback networks, re-checks every redirect hop, sends no credentials, and limits size and time.

## Deliberately out of scope

Real authentication, billing, SMS and email, legal agreements, bid submission, the Nova Scotia provincial portal (no verified API), OCR, and win or eligibility predictions.
