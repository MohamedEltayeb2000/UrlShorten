package com.example.shortener.orchestrator;

import java.util.List;

/** The three seeded demonstration scenarios. Launching one creates an ordinary run with the preset requirement. */
public final class ScenarioPresets {
    public record Preset(String id, String name, String description, String requirement) {}

    private static final List<Preset> ALL = List.of(
            new Preset("GREENFIELD", "Greenfield",
                    "Build a new URL shortener from scratch: requirement normalization, task graph, parallel architecture and "
                            + "implementation planning, a patch proposal behind an approval gate, tests, docs and a release checklist.",
                    "Build a URL shortener API with link creation, redirects, expiration, and click analytics."),
            new Preset("BROWNFIELD", "Brownfield",
                    "Extend a small existing codebase (a seeded fixture): impacted files and data flow are derived from the fixture, "
                            + "with a proposed idempotency change and compatibility risks.",
                    "Add idempotent link creation and prevent duplicate links when clients retry requests."),
            new Preset("AMBIGUOUS", "Ambiguous requirement",
                    "A vague requirement: the analyst records open questions, assumptions and classified risks, and the run waits "
                            + "for a human decision before proceeding on the safe assumptions.",
                    "Make URL links safer and improve analytics."));

    private ScenarioPresets() {}

    public static List<Preset> all() {
        return ALL;
    }

    public static Preset byId(String id) {
        return ALL.stream().filter(p -> p.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown scenario " + id));
    }
}
