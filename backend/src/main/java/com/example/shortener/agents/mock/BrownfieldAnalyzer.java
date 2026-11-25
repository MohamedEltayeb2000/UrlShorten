package com.example.shortener.agents.mock;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Structural analysis of a Java source directory (read-only, no compilation, no extra dependency). It scans
 * {@code .java} and {@code .sql} files with regex and line scanning, builds a class dependency graph
 * (controller to service to repository), finds HTTP endpoints and SQL tables, and from that derives the impacted
 * files, the data flow and compatibility risks for a requirement. Comments are stripped first, so the findings
 * never depend on comment text. Deterministic: files are processed in sorted order.
 *
 * <p>Simplifying assumption: one primary type per file; only members at the top level of that type are read.
 */
public class BrownfieldAnalyzer {

    /** Result of analysing a requirement against the code. All lists are sorted or in stable order. */
    public record Analysis(List<String> impactedFiles, List<String> dataFlow, List<String> compatibilityRisks) {}

    /** What the scan found, independent of any requirement. */
    public record Structure(Map<String, List<String>> dependencies, Map<String, String> roles, List<String> endpoints,
            List<String> tables) {}

    /** System property (or env var SHORTENER_BROWNFIELD_PATH) naming the source directory to analyse. */
    public static final String PATH_PROPERTY = "shortener.brownfield.path";

    private final Path root;

    public BrownfieldAnalyzer() {
        this(null);
    }

    /** @param root directory to scan; null means the seeded fixture. */
    public BrownfieldAnalyzer(Path root) {
        this.root = root;
    }

    /** Uses {@code shortener.brownfield.path} when set, otherwise the seeded fixture. */
    public static BrownfieldAnalyzer fromConfiguredPath() {
        String p = System.getProperty(PATH_PROPERTY);
        if (p == null || p.isBlank()) p = System.getenv("SHORTENER_BROWNFIELD_PATH");
        return p == null || p.isBlank() ? new BrownfieldAnalyzer() : new BrownfieldAnalyzer(Path.of(p.trim()));
    }

    /** Walks up from the working directory until fixtures/brownfield is found. */
    static Path locateFixture() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("fixtures").resolve("brownfield");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("fixtures/brownfield not found above " + Path.of("").toAbsolutePath());
    }

    // ---- model --------------------------------------------------------------------------------------

    private static final class Method {
        String name;
        String params = "";
        String httpMethod;
        String path;
        final List<String[]> calls = new ArrayList<>();
        final Set<String> reads = new TreeSet<>();
        final Set<String> writes = new TreeSet<>();
        final Map<String, List<String>> insertColumns = new TreeMap<>();
    }

    private static final class Cls {
        String name;
        String file;
        String basePath = "";
        final Set<String> annotations = new TreeSet<>();
        final Map<String, String> vars = new LinkedHashMap<>();
        final Set<String> referencedTypes = new TreeSet<>();
        final Map<String, Method> methods = new LinkedHashMap<>();
        String entityTable;
        String repoEntity;
        boolean controller;
        boolean repository;
        boolean service;
    }

    private static final class Table {
        String name;
        String file;
        final Set<String> uniqueColumns = new TreeSet<>();
        final Set<String> columns = new TreeSet<>();
    }

    private static final class Model {
        final Map<String, Cls> classes = new TreeMap<>();
        final Map<String, Table> tables = new TreeMap<>();
        final Set<String> codeTables = new TreeSet<>();
    }

    // ---- public API ---------------------------------------------------------------------------------

    /** Analyses the whole code base (every controller counts as impacted). */
    public Analysis analyze() {
        return analyze(null);
    }

    /** Analyses the code base for a requirement: files and tables matching its terms, and the paths between them. */
    public Analysis analyze(String requirement) {
        Model m = scan();
        Set<String> seeds = new TreeSet<>();
        Set<String> seedTables = new TreeSet<>();
        if (requirement == null || requirement.isBlank()) {
            for (Cls c : m.classes.values()) if (c.controller) seeds.add(c.name);
        } else {
            Set<String> terms = requirementStems(requirement);
            for (Cls c : m.classes.values()) {
                if (intersects(terms, nameStems(c.name, true))) seeds.add(c.name);
                for (Method me : c.methods.values()) {
                    if (c.controller && me.httpMethod != null && intersects(terms, pathStems(fullPath(c, me)))) {
                        seeds.add(c.name);
                    }
                }
            }
            Set<String> allTables = new TreeSet<>(m.tables.keySet());
            allTables.addAll(m.codeTables);
            for (String t : allTables) if (intersects(terms, nameStems(t, false))) seedTables.add(t);
            for (Cls c : m.classes.values()) if (!Collections.disjoint(touchedTables(c), seedTables)) seeds.add(c.name);
        }

        Map<String, Set<String>> graph = dependencyGraph(m);
        Set<String> impacted = new TreeSet<>();
        for (String s : seeds) {
            impacted.add(s);
            impacted.addAll(reachable(graph, s));
        }
        for (Cls c : m.classes.values()) {
            if (!c.controller || impacted.contains(c.name)) continue;
            Set<String> reach = reachable(graph, c.name);
            Set<String> hit = new TreeSet<>(reach);
            hit.retainAll(seeds);
            if (hit.isEmpty()) continue;
            impacted.add(c.name);
            for (String n : reach) {
                if (seeds.contains(n) || !Collections.disjoint(reachable(graph, n), seeds)) impacted.add(n);
            }
        }
        Set<String> tables = new TreeSet<>(seedTables);
        for (String cn : impacted) tables.addAll(touchedTables(m.classes.get(cn)));

        Set<String> files = new TreeSet<>();
        for (String cn : impacted) files.add(m.classes.get(cn).file);
        for (String t : tables) if (m.tables.containsKey(t)) files.add(m.tables.get(t).file);

        List<String> flow = dataFlow(m, impacted);
        List<String> risks = risks(m, impacted, tables);
        return new Analysis(List.copyOf(files), List.copyOf(flow), List.copyOf(risks));
    }

    /** The raw structural findings: dependency graph, roles, endpoints and tables. */
    public Structure structure() {
        Model m = scan();
        Map<String, List<String>> deps = new TreeMap<>();
        dependencyGraph(m).forEach((k, v) -> deps.put(k, List.copyOf(v)));
        Map<String, String> roles = new TreeMap<>();
        for (Cls c : m.classes.values()) roles.put(c.name, role(c));
        List<String> endpoints = new ArrayList<>();
        for (Cls c : m.classes.values()) {
            for (Method me : c.methods.values()) {
                if (me.httpMethod != null) endpoints.add(me.httpMethod + " " + fullPath(c, me));
            }
        }
        endpoints.sort(null);
        Set<String> tables = new TreeSet<>(m.tables.keySet());
        tables.addAll(m.codeTables);
        return new Structure(deps, roles, List.copyOf(endpoints), List.copyOf(tables));
    }

    // ---- scanning -----------------------------------------------------------------------------------

    private static final Pattern TYPE_DECL = Pattern.compile("\\b(class|interface|record|enum)\\s+(\\w+)");
    private static final Pattern ANNOTATION = Pattern.compile("@(\\w+)(?:\\s*\\(([^)]*)\\))?");
    private static final Pattern STRING_LIT = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern METHOD_WITH_BODY = Pattern.compile(
            "(\\w+)\\s*\\(([^()]*)\\)\\s*(?:throws\\s+[\\w.,\\s]+)?\\{");
    private static final Pattern METHOD_ABSTRACT = Pattern.compile("(\\w+)\\s*\\(([^()]*)\\)\\s*;");
    private static final Pattern FIELD = Pattern.compile(
            "(?:private|protected|public)?\\s*(?:static\\s+)?(?:final\\s+)?([A-Z]\\w*(?:<[^>;]*>)?)\\s+(\\w+)\\s*(?:=[^;]*)?;");
    private static final Pattern CALL = Pattern.compile("(?:this\\.)?\\b(\\w+)\\.(\\w+)\\s*\\(");
    private static final Pattern SQL_INSERT = Pattern.compile("(?i)\\binsert\\s+into\\s+(\\w+)\\s*(?:\\(([^)]*)\\))?");
    private static final Pattern SQL_UPDATE = Pattern.compile("(?i)\\bupdate\\s+(\\w+)\\s+set\\b");
    private static final Pattern SQL_DELETE = Pattern.compile("(?i)\\bdelete\\s+from\\s+(\\w+)");
    private static final Pattern SQL_READ = Pattern.compile("(?i)\\b(?:from|join)\\s+(\\w+)");
    private static final Set<String> KEYWORDS = Set.of("if", "for", "while", "switch", "catch", "try", "synchronized",
            "return", "new", "else", "do", "throw", "assert");

    private Model scan() {
        Path dir = root != null ? root : locateFixture();
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Brownfield source directory not found or not a directory: " + dir);
        }
        Model m = new Model();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(dir)) {
            files = walk.filter(Files::isRegularFile).filter(f -> {
                String n = f.getFileName().toString();
                return n.endsWith(".java") || n.endsWith(".sql");
            }).filter(f -> !skipped(dir, f)).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (Path f : files) {
            String rel = dir.relativize(f).toString().replace('\\', '/');
            String text;
            try {
                text = Files.readString(f, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (rel.endsWith(".sql")) parseSql(m, rel, text);
            else parseJava(m, rel, stripComments(text));
        }
        // Keep only dependencies on classes that exist in the scanned sources.
        for (Cls c : m.classes.values()) {
            c.referencedTypes.retainAll(m.classes.keySet());
            c.referencedTypes.remove(c.name);
            for (Method me : c.methods.values()) {
                me.calls.removeIf(call -> !m.classes.containsKey(call[0]));
            }
        }
        // Spring Data repositories touch their entity's table.
        for (Cls c : m.classes.values()) {
            if (c.repoEntity != null && m.classes.containsKey(c.repoEntity)) {
                c.entityTable = m.classes.get(c.repoEntity).entityTable;
            }
        }
        return m;
    }

    private static boolean skipped(Path root, Path f) {
        for (Path part : root.relativize(f)) {
            String n = part.toString();
            if (n.equals("target") || n.equals("build") || n.equals("node_modules") || n.startsWith(".")) return true;
        }
        return false;
    }

    private static void parseSql(Model m, String rel, String text) {
        String sql = text.replaceAll("(?m)--.*$", "").replaceAll("(?s)/\\*.*?\\*/", " ");
        Matcher t = Pattern.compile("(?is)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?(\\w+)\\s*\\((.*?)\\)\\s*;")
                .matcher(sql);
        while (t.find()) {
            Table tb = new Table();
            tb.name = t.group(1).toLowerCase(Locale.ROOT);
            tb.file = rel;
            for (String col : t.group(2).split(",(?![^()]*\\))")) {
                String c = col.trim();
                if (c.isEmpty()) continue;
                String lower = c.toLowerCase(Locale.ROOT);
                Matcher tc = Pattern.compile("(?i)^(?:constraint\\s+\\w+\\s+)?(unique|primary\\s+key)\\s*\\(([^)]*)\\)")
                        .matcher(c);
                if (tc.find()) {
                    String[] cols = tc.group(2).split(",");
                    if (cols.length == 1) tb.uniqueColumns.add(cols[0].trim().toLowerCase(Locale.ROOT));
                    continue;
                }
                String name = c.split("\\s+")[0].toLowerCase(Locale.ROOT);
                tb.columns.add(name);
                if (lower.contains("unique") || lower.contains("primary key")) tb.uniqueColumns.add(name);
            }
            m.tables.put(tb.name, tb);
        }
        Matcher ui = Pattern.compile("(?i)create\\s+unique\\s+index\\s+\\w+\\s+on\\s+(\\w+)\\s*\\(([^)]*)\\)").matcher(sql);
        while (ui.find()) {
            Table tb = m.tables.get(ui.group(1).toLowerCase(Locale.ROOT));
            String[] cols = ui.group(2).split(",");
            if (tb != null && cols.length == 1) tb.uniqueColumns.add(cols[0].trim().toLowerCase(Locale.ROOT));
        }
    }

    private static void parseJava(Model m, String rel, String code) {
        Matcher td = TYPE_DECL.matcher(code);
        if (!td.find()) return;
        Cls c = new Cls();
        c.name = td.group(2);
        c.file = rel;
        int typeStart = td.start();
        String before = code.substring(Math.max(0, lastBoundary(code, typeStart) + 1), typeStart);
        Matcher an = ANNOTATION.matcher(before);
        while (an.find()) {
            c.annotations.add(an.group(1));
            String arg = an.group(2) == null ? "" : an.group(2);
            if (an.group(1).equals("RequestMapping")) c.basePath = firstString(arg);
            if (an.group(1).equals("Table")) c.entityTable = attrString(arg, "name");
        }
        if (c.annotations.contains("Entity") && c.entityTable == null) c.entityTable = snake(c.name);
        int open = code.indexOf('{', td.end());
        String header = code.substring(td.end(), open < 0 ? code.length() : open);
        if (td.group(1).equals("record")) {
            int p = header.indexOf('(');
            int q = header.lastIndexOf(')');
            if (p >= 0 && q > p) addParams(c, header.substring(p + 1, q));
        }
        Matcher ext = Pattern.compile("\\w*Repository\\s*<\\s*(\\w+)").matcher(header);
        if (ext.find()) c.repoEntity = ext.group(1);
        if (open < 0) {
            register(m, c);
            return;
        }

        int[] depth = depths(code);
        // Fields at the top level of the type.
        Matcher f = FIELD.matcher(code);
        while (f.find()) {
            if (f.start() > open && depth[f.start(1)] == 1) {
                String type = f.group(1).replaceAll("<.*", "");
                c.vars.put(f.group(2), type);
                c.referencedTypes.add(type);
                addGenerics(c, f.group(1));
            }
        }
        Set<String> seenAt = new TreeSet<>();
        Matcher mb = METHOD_WITH_BODY.matcher(code);
        while (mb.find()) {
            if (mb.start() <= open || depth[mb.start()] != 1 || KEYWORDS.contains(mb.group(1))) continue;
            int bodyOpen = mb.end() - 1;
            int bodyEnd = matchBrace(code, bodyOpen);
            String body = code.substring(bodyOpen, bodyEnd);
            readMethod(c, code, mb.start(), mb.group(1), mb.group(2), body);
            seenAt.add(mb.group(1) + "@" + mb.start());
        }
        Matcher ma = METHOD_ABSTRACT.matcher(code);
        while (ma.find()) {
            if (ma.start() <= open || depth[ma.start()] != 1 || KEYWORDS.contains(ma.group(1))) continue;
            // Skip field initialisers such as "x = foo();".
            String lead = code.substring(lastBoundary(code, ma.start()) + 1, ma.start());
            if (lead.contains("=") || lead.contains("return") || lead.contains("new ") || lead.isBlank()) continue;
            readMethod(c, code, ma.start(), ma.group(1), ma.group(2), "");
        }
        register(m, c);
    }

    private static void register(Model m, Cls c) {
        c.controller = c.annotations.contains("RestController") || c.annotations.contains("Controller")
                || c.name.endsWith("Controller") || c.methods.values().stream().anyMatch(x -> x.httpMethod != null);
        c.repository = c.annotations.contains("Repository") || c.name.endsWith("Repository") || c.name.endsWith("Dao")
                || c.repoEntity != null;
        c.service = c.annotations.contains("Service") || c.name.endsWith("Service");
        if (c.entityTable != null) m.codeTables.add(c.entityTable.toLowerCase(Locale.ROOT));
        for (Method me : c.methods.values()) {
            m.codeTables.addAll(me.reads);
            m.codeTables.addAll(me.writes);
        }
        m.classes.put(c.name, c);
    }

    private static void readMethod(Cls c, String code, int start, String name, String params, String body) {
        String lead = code.substring(lastBoundary(code, start) + 1, start);
        Method me = c.methods.get(name);
        boolean ctor = name.equals(c.name);
        if (me == null) {
            me = new Method();
            me.name = name;
            me.params = params.trim().replaceAll("\\s+", " ");
            if (!ctor) c.methods.put(name, me);
        }
        if (ctor) {
            addParams(c, params);
            return;
        }
        StringBuilder sql = new StringBuilder();
        Matcher an = ANNOTATION.matcher(lead);
        while (an.find()) {
            String arg = an.group(2) == null ? "" : an.group(2);
            String http = switch (an.group(1)) {
                case "PostMapping" -> "POST";
                case "GetMapping" -> "GET";
                case "PutMapping" -> "PUT";
                case "DeleteMapping" -> "DELETE";
                case "PatchMapping" -> "PATCH";
                case "RequestMapping" -> {
                    Matcher rm = Pattern.compile("RequestMethod\\.(\\w+)").matcher(arg);
                    yield rm.find() ? rm.group(1) : "GET";
                }
                default -> null;
            };
            if (http != null) {
                me.httpMethod = http;
                String p = attrString(arg, "value");
                if (p == null) p = attrString(arg, "path");
                me.path = p != null ? p : firstString(arg);
            }
            if (an.group(1).equals("Query")) sql.append(' ').append(arg);
        }
        Matcher lits = STRING_LIT.matcher(body);
        while (lits.find()) sql.append(' ').append(lits.group(1));
        scanSql(me, sql.toString());
        Matcher call = CALL.matcher(body);
        while (call.find()) {
            String recv = call.group(1);
            String type = c.vars.get(recv);
            if (type != null) me.calls.add(new String[] {type, call.group(2)});
            else if (Character.isUpperCase(recv.charAt(0))) me.calls.add(new String[] {recv, call.group(2)});
        }
    }

    private static void scanSql(Method me, String sql) {
        Matcher i = SQL_INSERT.matcher(sql);
        while (i.find()) {
            String t = i.group(1).toLowerCase(Locale.ROOT);
            me.writes.add(t);
            if (i.group(2) != null) {
                List<String> cols = new ArrayList<>();
                for (String col : i.group(2).split(",")) cols.add(col.trim().toLowerCase(Locale.ROOT));
                me.insertColumns.put(t, cols);
            }
        }
        Matcher u = SQL_UPDATE.matcher(sql);
        while (u.find()) me.writes.add(u.group(1).toLowerCase(Locale.ROOT));
        Matcher d = SQL_DELETE.matcher(sql);
        while (d.find()) me.writes.add(d.group(1).toLowerCase(Locale.ROOT));
        Matcher r = SQL_READ.matcher(sql);
        while (r.find()) {
            String t = r.group(1).toLowerCase(Locale.ROOT);
            if (!t.equals("dual")) me.reads.add(t);
        }
    }

    private static void addParams(Cls c, String params) {
        for (String p : params.split(",(?![^<]*>)")) {
            String[] parts = p.trim().replaceAll("@\\w+(\\([^)]*\\))?\\s*", "").replaceAll("final\\s+", "").split("\\s+");
            if (parts.length >= 2) {
                String type = parts[parts.length - 2].replaceAll("<.*", "");
                if (!type.isEmpty() && Character.isUpperCase(type.charAt(0))) {
                    c.vars.putIfAbsent(parts[parts.length - 1], type);
                    c.referencedTypes.add(type);
                }
            }
        }
    }

    private static void addGenerics(Cls c, String type) {
        Matcher g = Pattern.compile("[A-Z]\\w*").matcher(type.replaceFirst("^[A-Z]\\w*", ""));
        while (g.find()) c.referencedTypes.add(g.group());
    }

    // ---- lexical helpers ----------------------------------------------------------------------------

    /** Removes // and block comments while leaving string and char literals intact. */
    static String stripComments(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        while (i < n) {
            char ch = s.charAt(i);
            if (ch == '"' || ch == '\'') {
                int j = i + 1;
                while (j < n && s.charAt(j) != ch) {
                    if (s.charAt(j) == '\\') j++;
                    j++;
                }
                out.append(s, i, Math.min(n, j + 1));
                i = j + 1;
            } else if (ch == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') i++;
            } else if (ch == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int end = s.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                out.append(' ');
            } else {
                out.append(ch);
                i++;
            }
        }
        return out.toString();
    }

    /** Brace depth before each character (strings ignored). */
    private static int[] depths(String code) {
        int[] d = new int[code.length() + 1];
        int depth = 0;
        boolean inStr = false;
        for (int i = 0; i < code.length(); i++) {
            char ch = code.charAt(i);
            if (ch == '"' && (i == 0 || code.charAt(i - 1) != '\\')) inStr = !inStr;
            d[i] = depth;
            if (!inStr) {
                if (ch == '{') depth++;
                else if (ch == '}') depth--;
            }
        }
        d[code.length()] = depth;
        return d;
    }

    private static int matchBrace(String code, int open) {
        int depth = 0;
        boolean inStr = false;
        for (int i = open; i < code.length(); i++) {
            char ch = code.charAt(i);
            if (ch == '"' && code.charAt(i - 1) != '\\') inStr = !inStr;
            if (inStr) continue;
            if (ch == '{') depth++;
            else if (ch == '}' && --depth == 0) return i + 1;
        }
        return code.length();
    }

    private static int lastBoundary(String code, int pos) {
        int best = -1;
        boolean inParen = false;
        for (int i = pos - 1; i >= 0; i--) {
            char ch = code.charAt(i);
            if (ch == ')') inParen = true;
            if (ch == '(') inParen = false;
            if (!inParen && (ch == ';' || ch == '{' || ch == '}')) {
                best = i;
                break;
            }
        }
        return best;
    }

    private static String firstString(String arg) {
        Matcher m = STRING_LIT.matcher(arg);
        return m.find() ? m.group(1) : "";
    }

    private static String attrString(String arg, String attr) {
        Matcher m = Pattern.compile("\\b" + attr + "\\s*=\\s*\"([^\"]*)\"").matcher(arg);
        return m.find() ? m.group(1) : null;
    }

    private static String snake(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    // ---- graph and matching -------------------------------------------------------------------------

    private static Map<String, Set<String>> dependencyGraph(Model m) {
        Map<String, Set<String>> g = new TreeMap<>();
        for (Cls c : m.classes.values()) {
            Set<String> out = new TreeSet<>(c.referencedTypes);
            for (Method me : c.methods.values()) for (String[] call : me.calls) out.add(call[0]);
            out.remove(c.name);
            g.put(c.name, out);
        }
        return g;
    }

    private static Set<String> reachable(Map<String, Set<String>> g, String from) {
        Set<String> seen = new TreeSet<>();
        Deque<String> q = new ArrayDeque<>(g.getOrDefault(from, Set.of()));
        while (!q.isEmpty()) {
            String n = q.poll();
            if (n.equals(from) || !seen.add(n)) continue;
            q.addAll(g.getOrDefault(n, Set.of()));
        }
        return seen;
    }

    private static Set<String> touchedTables(Cls c) {
        Set<String> t = new TreeSet<>();
        if (c.entityTable != null) t.add(c.entityTable.toLowerCase(Locale.ROOT));
        for (Method me : c.methods.values()) {
            t.addAll(me.reads);
            t.addAll(me.writes);
        }
        return t;
    }

    private static String role(Cls c) {
        if (c.controller) return "CONTROLLER";
        if (c.repository) return "REPOSITORY";
        if (c.service) return "SERVICE";
        if (c.annotations.contains("Entity")) return "ENTITY";
        return "OTHER";
    }

    private static String fullPath(Cls c, Method me) {
        String p = (c.basePath + (me.path == null ? "" : me.path)).replaceAll("/{2,}", "/");
        return p.isEmpty() ? "/" : (p.startsWith("/") ? p : "/" + p);
    }

    private static final Set<String> NOISE = Set.of("a", "an", "the", "and", "or", "of", "to", "in", "on", "for",
            "with", "by", "from", "at", "as", "is", "are", "be", "that", "this", "it", "when", "if", "so", "not", "no",
            "add", "make", "create", "creation", "build", "prevent", "request", "client", "api", "new", "existing",
            "support", "allow", "ensure", "get", "post", "put", "delete", "retry", "controller", "service",
            "repository", "dao", "impl", "manager", "handler", "demo", "main", "java", "id");

    private static Set<String> requirementStems(String requirement) {
        Set<String> out = new TreeSet<>();
        for (String w : requirement.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (w.length() > 1 && !NOISE.contains(w) && !NOISE.contains(stem(w))) out.add(stem(w));
        }
        return out;
    }

    private static Set<String> nameStems(String name, boolean camel) {
        Set<String> out = new TreeSet<>();
        String spaced = camel ? name.replaceAll("([a-z0-9])([A-Z])", "$1 $2") : name.replace('_', ' ');
        for (String w : spaced.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (w.length() > 1 && !NOISE.contains(w)) out.add(stem(w));
        }
        return out;
    }

    private static Set<String> pathStems(String path) {
        Set<String> out = new TreeSet<>();
        for (String w : path.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (w.length() > 1 && !NOISE.contains(w)) out.add(stem(w));
        }
        return out;
    }

    private static String stem(String w) {
        if (w.endsWith("ies") && w.length() > 4) return w.substring(0, w.length() - 3) + "y";
        if (w.endsWith("s") && !w.endsWith("ss") && w.length() > 3) return w.substring(0, w.length() - 1);
        return w;
    }

    private static boolean intersects(Set<String> a, Set<String> b) {
        for (String s : b) if (a.contains(s)) return true;
        return false;
    }

    // ---- derived findings ---------------------------------------------------------------------------

    private List<String> dataFlow(Model m, Set<String> impacted) {
        Set<String> flows = new TreeSet<>();
        Set<String> visitedClasses = new TreeSet<>();
        for (String cn : impacted) {
            Cls c = m.classes.get(cn);
            if (!c.controller) continue;
            for (Method me : c.methods.values()) {
                if (me.httpMethod == null) continue;
                walk(m, c, me, me.httpMethod + " " + fullPath(c, me) + " -> " + c.name + "." + me.name, flows,
                        new TreeSet<>(), visitedClasses);
            }
        }
        for (String cn : impacted) {
            Cls c = m.classes.get(cn);
            if (c.controller || visitedClasses.contains(cn)) continue;
            for (Method me : c.methods.values()) {
                if (!me.calls.isEmpty() || !touched(m, c, me).isEmpty()) {
                    walk(m, c, me, c.name + "." + me.name, flows, new TreeSet<>(), visitedClasses);
                }
            }
        }
        return new ArrayList<>(flows);
    }

    private void walk(Model m, Cls c, Method me, String path, Set<String> flows, Set<String> onPath,
            Set<String> visitedClasses) {
        visitedClasses.add(c.name);
        if (!onPath.add(c.name + "." + me.name)) return;
        Set<String> tables = touched(m, c, me);
        for (String t : tables) flows.add(path + " -> table " + t);
        boolean descended = false;
        for (String[] call : me.calls) {
            Cls target = m.classes.get(call[0]);
            if (target == null) continue;
            descended = true;
            Method tm = target.methods.get(call[1]);
            String next = path + " -> " + target.name + "." + call[1];
            if (tm == null) {
                visitedClasses.add(target.name);
                if (target.entityTable != null) flows.add(next + " -> table " + target.entityTable.toLowerCase(Locale.ROOT));
                else flows.add(next);
            } else {
                walk(m, target, tm, next, flows, onPath, visitedClasses);
            }
        }
        if (!descended && tables.isEmpty()) flows.add(path);
        onPath.remove(c.name + "." + me.name);
    }

    private static Set<String> touched(Model m, Cls c, Method me) {
        Set<String> t = new TreeSet<>(me.reads);
        t.addAll(me.writes);
        if (t.isEmpty() && c.repoEntity != null && c.entityTable != null) t.add(c.entityTable.toLowerCase(Locale.ROOT));
        return t;
    }

    private List<String> risks(Model m, Set<String> impacted, Set<String> tables) {
        List<String> risks = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        // Uniqueness: columns written by an insert that carry no unique constraint.
        for (String cn : impacted) {
            Cls c = m.classes.get(cn);
            for (Method me : c.methods.values()) {
                for (Map.Entry<String, List<String>> ins : me.insertColumns.entrySet()) {
                    Table tb = m.tables.get(ins.getKey());
                    if (tb == null) continue;
                    for (String col : ins.getValue()) {
                        if (tb.columns.contains(col) && !tb.uniqueColumns.contains(col)
                                && !col.equals("created_at") && !col.equals("updated_at")
                                && seen.add(tb.name + "." + col)) {
                            risks.add("Table " + tb.name + " has no uniqueness constraint on column " + col + ", which "
                                    + c.name + "." + me.name + " writes with an INSERT, so the same value can be stored more than once");
                        }
                    }
                }
            }
        }
        // Insert without an existence check in the calling method.
        for (String cn : impacted) {
            Cls c = m.classes.get(cn);
            for (Method me : c.methods.values()) {
                if (me.writes.isEmpty() || me.insertColumns.isEmpty()) continue;
                for (String t : me.insertColumns.keySet()) {
                    List<String[]> callers = callersOf(m, impacted, c.name, me.name);
                    if (callers.isEmpty()) {
                        callers = new ArrayList<>();
                        callers.add(new String[] {c.name, me.name});
                    }
                    for (String[] caller : callers) {
                        if (!checksExistence(m, m.classes.get(caller[0]).methods.get(caller[1]), t, me)) {
                            String endpoint = endpointFor(m, impacted, caller[0], caller[1]);
                            String msg = caller[0] + "." + caller[1]
                                    + (caller[0].equals(c.name) && caller[1].equals(me.name) ? "" : " calls " + c.name + "." + me.name)
                                    + " and inserts into " + t + " without checking for an existing row first, so repeating the call"
                                    + (endpoint.isEmpty() ? "" : " (for example a retried " + endpoint + ")")
                                    + " creates a duplicate";
                            if (seen.add(msg)) risks.add(msg);
                        }
                    }
                }
            }
        }
        // API contract of every impacted endpoint.
        for (String cn : impacted) {
            Cls c = m.classes.get(cn);
            if (!c.controller) continue;
            for (Method me : c.methods.values()) {
                if (me.httpMethod == null) continue;
                risks.add("API contract: " + me.httpMethod + " " + fullPath(c, me) + " (" + c.name + "." + me.name + "("
                        + me.params + ")) is an existing endpoint; keep its status code and response body shape, and make any new"
                        + " request header or field optional because current clients do not send it");
            }
        }
        // Schema migrations for touched tables.
        for (String t : tables) {
            Table tb = m.tables.get(t);
            risks.add(tb != null
                    ? "Schema migration: table " + t + " (" + tb.file + ") is touched; a constraint or column change needs a migration that tolerates existing rows, including existing duplicates"
                    : "Schema migration: table " + t + " is used in code but no CREATE TABLE was found in the scanned sources; confirm its schema before changing it");
        }
        return risks;
    }

    private static List<String[]> callersOf(Model m, Set<String> impacted, String cls, String method) {
        List<String[]> out = new ArrayList<>();
        for (String cn : impacted) {
            Cls c = m.classes.get(cn);
            for (Method me : c.methods.values()) {
                for (String[] call : me.calls) {
                    if (call[0].equals(cls) && call[1].equals(method)) out.add(new String[] {c.name, me.name});
                }
            }
        }
        return out;
    }

    private static final Pattern LOOKUP_NAME = Pattern.compile("(?i)^(find|get|exists|select|lookup|read|count|load|fetch|query).*");

    private static boolean checksExistence(Model m, Method caller, String table, Method writer) {
        if (caller == null) return false;
        if (caller != writer && caller.reads.contains(table)) return true;
        for (String[] call : caller.calls) {
            Cls target = m.classes.get(call[0]);
            if (target == null) continue;
            Method tm = target.methods.get(call[1]);
            if (tm == writer) continue;
            if (tm != null && tm.reads.contains(table)) return true;
            if (tm == null && table.equals(target.entityTable == null ? null : target.entityTable.toLowerCase(Locale.ROOT))
                    && LOOKUP_NAME.matcher(call[1]).matches()) return true;
        }
        return false;
    }

    private static String endpointFor(Model m, Set<String> impacted, String cls, String method) {
        for (String cn : impacted) {
            Cls c = m.classes.get(cn);
            if (!c.controller) continue;
            for (Method me : c.methods.values()) {
                if (me.httpMethod == null) continue;
                if (c.name.equals(cls) && me.name.equals(method)) return me.httpMethod + " " + fullPath(c, me);
                Set<String> visited = new TreeSet<>();
                walkClasses(m, c, me, visited, new TreeSet<>());
                if (visited.contains(cls + "." + method)) return me.httpMethod + " " + fullPath(c, me);
            }
        }
        return "";
    }

    private static void walkClasses(Model m, Cls c, Method me, Set<String> visited, Set<String> guard) {
        if (!guard.add(c.name + "." + me.name)) return;
        visited.add(c.name + "." + me.name);
        for (String[] call : me.calls) {
            Cls t = m.classes.get(call[0]);
            if (t == null) continue;
            Method tm = t.methods.get(call[1]);
            if (tm != null) walkClasses(m, t, tm, visited, guard);
        }
    }
}
