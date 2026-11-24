# Residual dependency risk (as of 2026-11-24)

The OWASP Dependency-Check scan (CVSS >= 7 fails the gate) first reported findings in Spring Boot 3.5.6, Spring Framework 6.2.11,
Tomcat 10.1.46, the PostgreSQL JDBC driver, Jackson, log4j and the Swagger UI bundle. What was done:

| Component | Action | Result |
|-----------|--------|--------|
| Spring Boot | 3.5.6 -> 3.5.16 | Boot findings cleared |
| Tomcat | 10.1.55 (from BOM) -> 10.1.62 (override) | Cleared |
| PostgreSQL JDBC | 42.7.11 -> 42.7.15 (override) | Cleared |
| log4j-api | 2.24.3 -> 2.25.5 (override) | Cleared |
| Swagger UI webjar (DOMPurify) | 5.32.2 -> 5.36.0 (override) | Cleared |
| Spring Framework (spring-core, spring-web) | 6.2.19 is the latest 6.2.x; no fixed release exists yet | **Residual - suppressed until 2026-12-31** |

## Residual: Spring Framework 6.2.0-6.2.19

Suppressed in `backend/dependency-check-suppressions.xml` (expires 2026-12-31 so it fails again after review). Owner-approved
waiver, 2026-11-24. Reachability in this application:

| CVE group | Affected feature | Used here? |
|-----------|------------------|------------|
| 47884 | `XsltView` with a catch-all view mapping | No - no view rendering, REST/JSON only |
| 47885, 47891, 47892, 47889, 47893 | WebFlux (part reader, Aalto XML, functional endpoints, Jetty adapter, WebSocket headers) | No - Spring MVC (servlet) only |
| 47888 | RSocket | No |
| 47890, 59313 | SSE with view fragments / functional SSE | No SSE endpoints |
| 47886, 59283 | SpEL with user-supplied expressions / `SimpleEvaluationContext` | No SpEL on user input |
| 59282 | Data binding of user-supplied property paths onto a target object | No - request bodies are bound by Jackson into immutable records; query parameters bind to simple types |

Follow-up (owner decision needed): move to the Spring Framework 6.2.x release that fixes these as soon as it is published, or to
Spring Boot 4 / Spring Framework 7. Re-run `scripts/gate-security.ps1` then and remove the suppression.
