package com.example.shortener.orchestrator;

import java.util.*;

/** Validates workflow graphs and computes deterministic orderings and levels. */
public final class GraphValidator {
    private GraphValidator() {}

    /** Throws {@link InvalidWorkflowException} listing all problems found. */
    public static void validate(WorkflowDefinition def) {
        List<String> problems = new ArrayList<>();
        if (def == null || def.nodes().isEmpty()) {
            throw new InvalidWorkflowException(List.of("workflow has no nodes"));
        }
        Set<String> ids = new LinkedHashSet<>();
        for (NodeDef n : def.nodes()) {
            if (n.id() == null || n.id().isBlank()) {
                problems.add("node has a blank id");
            } else if (!ids.add(n.id())) {
                problems.add("duplicate node id: " + n.id());
            }
        }
        for (NodeDef n : def.nodes()) {
            for (String dep : n.dependsOn()) {
                if (dep != null && dep.equals(n.id())) {
                    problems.add("node depends on itself: " + n.id());
                } else if (!ids.contains(dep)) {
                    problems.add("node " + n.id() + " depends on unknown node: " + dep);
                }
            }
        }
        findCycle(def, ids).ifPresent(c -> problems.add("cycle: " + String.join(" -> ", c)));
        if (!problems.isEmpty()) {
            throw new InvalidWorkflowException(problems);
        }
    }

    /** Dependencies-first order; ties broken by declaration order. */
    public static List<String> topologicalOrder(WorkflowDefinition def) {
        validate(def);
        List<String> order = new ArrayList<>();
        Set<String> done = new HashSet<>();
        while (order.size() < def.nodes().size()) {
            for (NodeDef n : def.nodes()) {
                if (!done.contains(n.id()) && done.containsAll(n.dependsOn())) {
                    order.add(n.id());
                    done.add(n.id());
                    break; // restart scan so earlier-declared nodes win ties
                }
            }
        }
        return order;
    }

    /** Level = length of the longest dependency path from an entry node (entry = 0). */
    public static Map<String, Integer> levels(WorkflowDefinition def) {
        Map<String, NodeDef> byId = new HashMap<>();
        def.nodes().forEach(n -> byId.put(n.id(), n));
        Map<String, Integer> levels = new LinkedHashMap<>();
        for (String id : topologicalOrder(def)) {
            int level = 0;
            for (String dep : byId.get(id).dependsOn()) {
                level = Math.max(level, levels.get(dep) + 1);
            }
            levels.put(id, level);
        }
        return levels;
    }

    private static Optional<List<String>> findCycle(WorkflowDefinition def, Set<String> ids) {
        Map<String, List<String>> deps = new HashMap<>();
        for (NodeDef n : def.nodes()) {
            if (n.id() == null) continue;
            deps.computeIfAbsent(n.id(), k -> new ArrayList<>()).addAll(n.dependsOn());
        }
        Set<String> finished = new HashSet<>();
        for (String id : ids) {
            Optional<List<String>> c = dfs(id, deps, finished, new ArrayList<>());
            if (c.isPresent()) return c;
        }
        return Optional.empty();
    }

    private static Optional<List<String>> dfs(
            String id, Map<String, List<String>> deps, Set<String> finished, List<String> path) {
        if (finished.contains(id)) return Optional.empty();
        int at = path.indexOf(id);
        if (at >= 0) {
            List<String> cycle = new ArrayList<>(path.subList(at, path.size()));
            cycle.add(id);
            return Optional.of(cycle);
        }
        path.add(id);
        for (String dep : deps.getOrDefault(id, List.of())) {
            if (dep == null || dep.equals(id) || !deps.containsKey(dep)) continue; // reported separately
            Optional<List<String>> c = dfs(dep, deps, finished, path);
            if (c.isPresent()) return c;
        }
        path.remove(path.size() - 1);
        finished.add(id);
        return Optional.empty();
    }
}
