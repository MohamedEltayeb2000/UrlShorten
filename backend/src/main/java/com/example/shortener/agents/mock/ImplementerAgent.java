package com.example.shortener.agents.mock;

import com.example.shortener.agents.AgentInput;
import com.example.shortener.agents.AgentResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Proposes code changes as a plain-text diff. It never writes files or runs anything: applied is always false. */
public class ImplementerAgent extends BaseMockAgent {

    private final BrownfieldAnalyzer analyzer;

    public ImplementerAgent() {
        this(new BrownfieldAnalyzer());
    }

    public ImplementerAgent(BrownfieldAnalyzer analyzer) {
        super("ImplementerAgent", "IMPLEMENTATION");
        this.analyzer = analyzer;
    }

    @Override
    protected AgentResult build(AgentInput in) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        List<String> risks = new ArrayList<>(List.of("MEDIUM: the proposal is untested until a human applies and runs it"));
        Map<String, Object> proposal;
        if (isScenario(in, BROWNFIELD)) {
            BrownfieldAnalyzer.Analysis a = analyzer.analyze();
            proposal = map(
                    "summary", "Make POST /links idempotent with an Idempotency-Key header, a request fingerprint and a unique key constraint",
                    "files", List.of(
                            map("path", "src/main/java/demo/LinkController.java", "change", "Read the optional Idempotency-Key header and pass it to the service"),
                            map("path", "src/main/java/demo/LinkService.java", "change", "Compute a fingerprint of the request and return the stored link on a replay"),
                            map("path", "src/main/resources/schema.sql", "change", "Add an idempotency_keys table with a unique key constraint")),
                    "diff", brownfieldDiff());
            out.put("impactedFiles", a.impactedFiles());
            out.put("dataFlow", a.dataFlow());
            out.put("compatibilityRisks", a.compatibilityRisks());
            risks.addAll(a.compatibilityRisks());
        } else {
            boolean links = isLinks(in);
            String file = links ? "src/main/java/app/LinkService.java" : "src/main/java/app/DomainService.java";
            proposal = map(
                    "summary", "Add the core behavior for: " + topic(in),
                    "files", List.of(map("path", file, "change", "Add the new method and input validation")),
                    "diff", genericDiff(file, links));
        }
        out.put("proposal", proposal);
        out.put("applied", Boolean.FALSE);
        return ok(in, "Proposed a change set (not applied): " + proposal.get("summary"), out,
                List.of("A human reviews the diff before anything is applied"), risks,
                List.of("Review the proposed diff", "Proceed to testing"));
    }

    private static String genericDiff(String file, boolean links) {
        String method = links ? "createLink(String targetUrl)" : "execute(String input)";
        return String.join("\n",
                "--- a/" + file,
                "+++ b/" + file,
                "@@ -10,0 +11,6 @@",
                "+    public Object " + method + " {",
                "+        if (" + (links ? "targetUrl" : "input") + " == null) {",
                "+            throw new IllegalArgumentException(\"input is required\");",
                "+        }",
                "+        return null; // placeholder for the proposed behavior",
                "+    }");
    }

    private static String brownfieldDiff() {
        return String.join("\n",
                "--- a/src/main/java/demo/LinkController.java",
                "+++ b/src/main/java/demo/LinkController.java",
                "@@ -13,3 +13,4 @@",
                "-    public Map<String, Object> postLink(Map<String, String> body) {",
                "+    public Map<String, Object> postLink(Map<String, String> body, String idempotencyKey) {",
                "         String targetUrl = body.get(\"targetUrl\");",
                "-        Link link = linkService.createLink(targetUrl);",
                "+        Link link = linkService.createLink(targetUrl, idempotencyKey);",
                "--- a/src/main/java/demo/LinkService.java",
                "+++ b/src/main/java/demo/LinkService.java",
                "@@ -16,5 +16,10 @@",
                "-    public Link createLink(String targetUrl) {",
                "+    public Link createLink(String targetUrl, String idempotencyKey) {",
                "+        String fingerprint = Fingerprint.sha256(targetUrl);",
                "+        if (idempotencyKey != null) {",
                "+            Link existing = repository.findByIdempotencyKey(idempotencyKey, fingerprint);",
                "+            if (existing != null) {",
                "+                return existing; // replay of an earlier request",
                "+            }",
                "+        }",
                "         String code = generateCode();",
                "--- a/src/main/resources/schema.sql",
                "+++ b/src/main/resources/schema.sql",
                "@@ -9,0 +10,7 @@",
                "+CREATE TABLE idempotency_keys (",
                "+    idempotency_key VARCHAR(128) NOT NULL,",
                "+    fingerprint     VARCHAR(64)  NOT NULL,",
                "+    link_id         BIGINT       NOT NULL,",
                "+    CONSTRAINT uq_idempotency_key UNIQUE (idempotency_key)",
                "+);");
    }
}
