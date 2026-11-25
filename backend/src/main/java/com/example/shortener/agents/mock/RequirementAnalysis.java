package com.example.shortener.agents.mock;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure, deterministic analysis of a requirement text: normalization, clause splitting, goal and constraint
 * extraction, actors and domain nouns, and detection of ambiguity from the text itself (vague terms,
 * missing acceptance criteria, very short text, conflicting hints, undefined references).
 */
public final class RequirementAnalysis {

    /** Score at or above which the requirement is treated as ambiguous. */
    public static final int THRESHOLD = 4;

    static final String VAGUE = "VAGUE_TERM";
    static final String MISSING_CRITERIA = "MISSING_ACCEPTANCE_CRITERIA";
    static final String TOO_SHORT = "TOO_SHORT";
    static final String CONFLICT = "CONFLICTING_HINTS";
    static final String UNDEFINED_REF = "UNDEFINED_REFERENCE";

    /** A specific reason the text is ambiguous. {@code term} is the offending word(s); weight feeds the score. */
    public record Trigger(String kind, String term, String detail, int weight) {}

    /** One goal: an action and the object it applies to. */
    public record Goal(String action, String object) {
        public String text() {
            return action + " " + object;
        }
    }

    public record Result(String normalized, List<String> clauses, List<Goal> goals, List<String> constraints,
            List<String> actors, List<String> domainTerms, List<Trigger> triggers, int score, boolean ambiguous) {

        public List<String> goalTexts() {
            return goals.stream().map(Goal::text).toList();
        }

        public List<String> vagueTerms() {
            return triggers.stream().filter(t -> t.kind().equals(VAGUE)).map(Trigger::term).toList();
        }
    }

    private static final Set<String> VAGUE_TERMS = Set.of(
            "safer", "safe", "secure", "safely", "better", "improve", "improved", "improving", "faster", "fast",
            "quick", "quicker", "scalable", "robust", "reliable", "user-friendly", "intuitive", "easy", "easier",
            "simple", "simpler", "modern", "optimize", "optimise", "optimized", "enhance", "enhanced", "efficient",
            "flexible", "powerful", "seamless", "nice", "good");

    private static final Set<String> VERBS = Set.of(
            "add", "build", "create", "make", "improve", "support", "allow", "enable", "prevent", "ensure",
            "provide", "implement", "expose", "track", "record", "reject", "validate", "redirect", "count", "limit",
            "remove", "fix", "optimize", "optimise", "enhance", "show", "send", "store", "log", "secure", "protect",
            "generate", "delete", "update", "migrate", "design", "develop", "introduce", "handle", "return", "let",
            "avoid", "block", "report", "expire", "shorten", "extend", "measure", "monitor", "export", "import");

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "and", "or", "of", "to", "in", "on", "for", "with", "by", "from", "at", "as", "is",
            "are", "be", "been", "was", "were", "that", "this", "it", "its", "their", "them", "they", "we", "our",
            "when", "if", "unless", "while", "so", "than", "then", "also", "can", "should", "must", "will", "not",
            "no", "only", "within", "more", "most", "least", "all", "any", "each", "every", "per", "into", "up",
            "existing", "new");

    private static final Set<String> ACTOR_WORDS = Set.of(
            "user", "users", "client", "clients", "admin", "admins", "administrator", "customer", "customers",
            "visitor", "visitors", "operator", "operators", "developer", "developers", "owner", "owners", "guest",
            "guests", "member", "members", "anonymous", "reviewer", "reviewers", "approver", "approvers");

    private static final Pattern CONSTRAINT_MARKER = Pattern.compile(
            "(?i)\\b(must not|must|only|within|at most|at least|no more than|no less than|never|cannot|can't|"
                    + "should not|shouldn't|without|requires?|required|up to|under|below|less than|more than|"
                    + "maximum|minimum|max|min)\\b|\\d");
    private static final Pattern MEASURE = Pattern.compile(
            "(?i)\\d|\\b(at most|at least|within|under|below|no more than|less than|p9\\d)\\b");
    private static final Pattern CONDITION = Pattern.compile("(?i)\\s+(when|whenever|if|unless|while|so that)\\s+");
    private static final Pattern WORD = Pattern.compile("[A-Za-z][A-Za-z'-]*");
    private static final Pattern PRONOUN = Pattern.compile("(?i)^(it|this|that|they|them|these|those)$");

    private static final String[][] CONFLICTS = {
            {"public", "private"}, {"anonymous", "authenticated"}, {"fast", "thorough"}, {"faster", "thorough"},
            {"simple", "comprehensive"}, {"simple", "fully-featured"}, {"cheap", "highly-available"},
            {"stateless", "stateful"}, {"synchronous", "asynchronous"}, {"open", "restricted"}};

    private RequirementAnalysis() {}

    /** Strips control characters, collapses whitespace and trims. Never returns null. */
    public static String normalize(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            sb.append(Character.isISOControl(c) ? ' ' : c);
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    public static Result analyze(String raw) {
        String normalized = normalize(raw);
        List<String> clauses = splitClauses(normalized);

        List<Goal> goals = new ArrayList<>();
        Set<String> constraints = new LinkedHashSet<>();
        String lastAction = null;
        for (String clause : clauses) {
            String head = clause;
            Matcher cond = CONDITION.matcher(clause);
            if (cond.find()) {
                head = clause.substring(0, cond.start()).trim();
                constraints.add("Condition: " + clause.substring(cond.start()).trim());
            }
            if (head.isEmpty()) continue;
            List<String> words = words(head);
            boolean verbFirst = !words.isEmpty() && VERBS.contains(words.get(0));
            boolean marked = CONSTRAINT_MARKER.matcher(head).find();
            if (marked) constraints.add(capitalize(head));
            if (verbFirst) {
                lastAction = capitalize(words.get(0));
                String object = head.substring(head.toLowerCase(Locale.ROOT).indexOf(words.get(0)) + words.get(0).length())
                        .trim();
                if (!object.isEmpty()) goals.add(new Goal(lastAction, object));
            } else if (!marked && !words.isEmpty()) {
                goals.add(new Goal(lastAction == null ? "Provide" : lastAction, head));
            }
        }
        if (goals.isEmpty() && !normalized.isEmpty()) {
            goals.add(new Goal("Deliver", normalized.length() > 140 ? normalized.substring(0, 140) : normalized));
        }

        Set<String> actors = new LinkedHashSet<>();
        Set<String> domain = new LinkedHashSet<>();
        for (String w : words(normalized)) {
            if (ACTOR_WORDS.contains(w)) actors.add(w);
            else if (w.length() > 2 && !STOPWORDS.contains(w) && !VERBS.contains(w) && !VAGUE_TERMS.contains(w)) {
                domain.add(w);
            }
        }

        List<Trigger> triggers = detect(normalized, clauses);
        int score = triggers.stream().mapToInt(Trigger::weight).sum();
        return new Result(normalized, List.copyOf(clauses), List.copyOf(goals), List.copyOf(constraints),
                List.copyOf(actors), List.copyOf(domain), List.copyOf(triggers), score, score >= THRESHOLD);
    }

    // ---- clause splitting ---------------------------------------------------------------------------

    static List<String> splitClauses(String normalized) {
        List<String> out = new ArrayList<>();
        String text = normalized.replaceAll("[.!?]+\\s*$", "");
        for (String part : text.split("(?i)\\s*(?:[;:,]|[.!?]\\s+|\\band\\b)\\s*")) {
            String p = part.trim();
            if (!p.isEmpty()) out.add(p);
        }
        return out;
    }

    private static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) out.add(m.group());
        return out;
    }

    // ---- ambiguity detection ------------------------------------------------------------------------

    private static List<Trigger> detect(String normalized, List<String> clauses) {
        List<Trigger> triggers = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < clauses.size(); i++) {
            boolean measured = MEASURE.matcher(clauses.get(i)).find()
                    || (i + 1 < clauses.size() && MEASURE.matcher(clauses.get(i + 1)).find());
            for (String w : words(clauses.get(i))) {
                if (VAGUE_TERMS.contains(w) && !measured && seen.add(w)) {
                    triggers.add(new Trigger(VAGUE, w,
                            "'" + w + "' has no measurable definition or limit", 2));
                }
            }
        }
        if (!seen.isEmpty()) {
            triggers.add(new Trigger(MISSING_CRITERIA, String.join(", ", seen),
                    "no numbers or limits define what counts as done for: " + String.join(", ", seen), 0));
        }
        List<String> all = words(normalized);
        if (!normalized.isEmpty() && all.size() < 6) {
            triggers.add(new Trigger(TOO_SHORT, normalized, "only " + all.size() + " words; too little detail", 1));
        }
        if (normalized.isEmpty()) {
            triggers.add(new Trigger(TOO_SHORT, "", "the requirement is empty", 1));
        }
        for (String[] pair : CONFLICTS) {
            if (all.contains(pair[0]) && all.contains(pair[1])) {
                triggers.add(new Trigger(CONFLICT, pair[0] + " vs " + pair[1],
                        "'" + pair[0] + "' and '" + pair[1] + "' may conflict", 2));
            }
        }
        // A pronoun in the first two words of the text has nothing earlier to refer to.
        for (int i = 0; i < Math.min(2, all.size()); i++) {
            if (PRONOUN.matcher(all.get(i)).matches()) {
                triggers.add(new Trigger(UNDEFINED_REF, all.get(i),
                        "'" + all.get(i) + "' has no antecedent in the requirement", 2));
                break;
            }
        }
        return triggers;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
