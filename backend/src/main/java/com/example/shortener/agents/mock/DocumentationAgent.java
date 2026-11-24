package com.example.shortener.agents.mock;

import com.example.shortener.agents.AgentInput;
import com.example.shortener.agents.AgentResult;
import java.util.List;
import java.util.Map;

/** Drafts the documents that should accompany the change. */
public class DocumentationAgent extends BaseMockAgent {

    public DocumentationAgent() {
        super("DocumentationAgent", "DOCUMENTATION");
    }

    @Override
    protected AgentResult build(AgentInput in) {
        String topic = topic(in);
        List<Object> docs = List.of(
                map("title", "Overview", "summary", "What was built and why: " + topic),
                map("title", "API usage", "summary", isLinks(in)
                        ? "How to create a short link and follow it, with request and response examples"
                        : "How to call the new behavior, with request and response examples"),
                map("title", "Operations notes", "summary", "Configuration, limits and known risks"));
        Map<String, Object> out = map("documents", docs);
        return ok(in, "Drafted " + docs.size() + " documents", out,
                List.of("Readers are developers familiar with the existing service"),
                List.of("LOW: documents can drift from the code if not reviewed with it"),
                List.of("Review the documents", "Proceed to release readiness"));
    }
}
