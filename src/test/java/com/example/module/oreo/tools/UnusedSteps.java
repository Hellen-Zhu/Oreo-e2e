package com.example.module.oreo.tools;

import com.example.module.oreo.testdata.ProjectPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * Lists the snippets and Java steps no feature reaches ({@code unused-steps}); writes nothing and never fails. It
 * walks from the steps of the feature files: a step marks every definition it matches, and a snippet's body, with the
 * captured values in place of {@code $1}, is walked in turn. So a snippet that only an unused snippet calls is unused
 * too: the report names those callers, as such a step does appear in snippet files, and lists the steps nothing calls
 * first, the ones to delete first. A Java step whose method other Java code calls is marked: only its annotation is
 * unused.
 */
final class UnusedSteps {

    private static final Pattern STEP = Pattern.compile("^\\s*(?:Given|When|Then|And|But|\\*)\\s+(.+?)\\s*$");
    private static final Pattern SNIPPET_HEADER = Pattern.compile("^@(?:Given|When|Then|And|But)\\s+\"(.*)\"\\s*$");
    private static final Pattern JAVA_STEP = Pattern.compile(
            "^\\s*@(?:Given|When|Then|And|But)\\(\\s*(?:value\\s*=\\s*)?\"((?:[^\"\\\\]|\\\\.)*)\"[^\\n]*\\n"
                    + "(?:\\s*@[^\\n]*\\n)*\\s*public\\s+\\S+\\s+(\\w+)\\(",
            Pattern.MULTILINE);
    private static final Pattern BLOCK = Pattern.compile("^\\s*(?:Feature|Rule|Background|Scenario|Scenario Outline):.*");
    private static final Pattern PLACEHOLDER = Pattern.compile("<[^<>]+>");

    /**
     * A snippet or a Java step.
     *
     * @param method the Java method, null for a snippet
     * @param body   the steps of a snippet, empty for a Java step
     */
    record Definition(String file, int line, String method, Pattern pattern, List<String> body) {}

    /** @param undefined the walked steps nothing here defines: Genie's built-ins, or a step that is missing */
    record Report(List<Definition> unused, Set<String> undefined) {}

    private UnusedSteps() {}

    static int run() throws IOException {
        Path project = ProjectPaths.projectRoot();
        List<Definition> definitions = new ArrayList<>();
        for (Path file : files(project.resolve("src/main/resources/snippet"), ".snippet")) {
            definitions.addAll(snippets(relative(project, file), read(file)));
        }
        Map<Path, String> java = new HashMap<>();
        for (Path file : files(project.resolve("src/main/java"), ".java")) {
            java.put(file, Files.readString(file));
        }
        for (Map.Entry<Path, String> file : java.entrySet()) {
            definitions.addAll(javaSteps(relative(project, file.getKey()), file.getValue()));
        }
        List<String> roots = new ArrayList<>();
        for (Path file : files(project.resolve("src/test/resources/features"), ".feature")) {
            roots.addAll(featureSteps(read(file)));
        }

        Report report = analyze(definitions, roots);
        System.out.println("unused-steps: " + definitions.size() + " definitions, walked from " + roots.size()
                + " feature steps");
        Map<Definition, List<Definition>> callers = callers(report.unused());
        print("Unused, nothing calls them, delete these first", report.unused(), callers, false, java.values());
        print("Unused, only the unused snippets after <- call them", report.unused(), callers, true, java.values());
        System.out.println("Steps with no definition here, Genie built-ins or missing (" + report.undefined().size() + "):");
        report.undefined().forEach(step -> System.out.println("  " + step));
        return 0;
    }

    static Report analyze(List<Definition> definitions, List<String> roots) {
        Set<Definition> used = new HashSet<>();
        Set<String> undefined = new TreeSet<>();
        Set<String> seen = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>(roots);
        while (!pending.isEmpty()) {
            String step = pending.pop();
            if (!seen.add(step)) continue;
            boolean defined = false;
            for (Definition definition : definitions) {
                Matcher matcher = definition.pattern().matcher(step);
                if (!matcher.find()) continue;
                defined = true;
                used.add(definition);
                for (String line : definition.body()) pending.push(withCaptures(line, matcher));
            }
            if (!defined) undefined.add(step);
        }
        List<Definition> unused = definitions.stream().filter(d -> !used.contains(d)).toList();
        return new Report(unused, undefined);
    }

    /** For each unused definition, the other unused snippets whose body calls it. */
    static Map<Definition, List<Definition>> callers(List<Definition> unused) {
        Map<Definition, List<Definition>> callers = new LinkedHashMap<>();
        for (Definition definition : unused) {
            callers.put(definition, unused.stream()
                    .filter(caller -> caller != definition)
                    .filter(caller -> caller.body().stream().anyMatch(line -> definition.pattern().matcher(line).find()))
                    .toList());
        }
        return callers;
    }

    private static void print(String title, List<Definition> unused, Map<Definition, List<Definition>> callers,
                              boolean called, Iterable<String> java) {
        List<Definition> listed = unused.stream().filter(d -> callers.get(d).isEmpty() != called).toList();
        System.out.println(title + " (" + listed.size() + "):");
        for (Definition definition : listed) {
            StringBuilder line = new StringBuilder("  " + definition.file() + ":" + definition.line() + "  ");
            if (definition.method() != null) line.append(definition.method()).append("  ");
            line.append(definition.pattern());
            if (definition.method() != null && calledFromJava(definition.method(), java)) {
                line.append("  [method called from Java]");
            }
            for (Definition caller : callers.get(definition)) {
                line.append("  <- ").append(caller.file()).append(":").append(caller.line());
            }
            System.out.println(line);
        }
    }

    private static String withCaptures(String line, Matcher matcher) {
        String result = line;
        for (int group = matcher.groupCount(); group > 0; group--) {
            String value = matcher.group(group);
            result = result.replace("$" + group, value == null ? "" : value);
        }
        return result;
    }

    /** The steps of a feature file, those of an Outline once per row of its {@code Examples}. */
    static List<String> featureSteps(List<String> lines) {
        List<String> steps = new ArrayList<>();
        List<String> block = new ArrayList<>();
        List<Map<String, String>> rows = new ArrayList<>();
        List<String> header = null;
        boolean inExamples = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (BLOCK.matcher(line).matches()) {
                expand(block, rows, steps);
                block = new ArrayList<>();
                rows = new ArrayList<>();
                header = null;
                inExamples = false;
            } else if (trimmed.startsWith("Examples:") || trimmed.startsWith("Scenarios:")) {
                inExamples = true;
                header = null;
            } else if (inExamples && trimmed.startsWith("|")) {
                List<String> cells = cells(trimmed);
                if (header == null) {
                    header = cells;
                } else {
                    Map<String, String> row = new HashMap<>();
                    for (int i = 0; i < Math.min(header.size(), cells.size()); i++) row.put(header.get(i), cells.get(i));
                    rows.add(row);
                }
            } else if (!inExamples) {
                Matcher step = STEP.matcher(line);
                if (step.matches()) block.add(step.group(1));
            }
        }
        expand(block, rows, steps);
        return steps;
    }

    private static void expand(List<String> block, List<Map<String, String>> rows, List<String> steps) {
        for (String step : block) {
            if (rows.isEmpty() || !PLACEHOLDER.matcher(step).find()) {
                steps.add(step);
                continue;
            }
            for (Map<String, String> row : rows) {
                String text = step;
                for (Map.Entry<String, String> cell : row.entrySet()) {
                    text = text.replace("<" + cell.getKey() + ">", cell.getValue());
                }
                steps.add(text);
            }
        }
    }

    private static List<String> cells(String row) {
        List<String> cells = new ArrayList<>();
        String[] parts = row.substring(1).split("\\|", -1);
        for (int i = 0; i < parts.length - 1; i++) cells.add(parts[i].trim());
        return cells;
    }

    static List<Definition> snippets(String file, List<String> lines) {
        List<Definition> definitions = new ArrayList<>();
        Pattern pattern = null;
        int headerLine = 0;
        List<String> body = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("@")) {
                if (pattern != null) definitions.add(new Definition(file, headerLine, null, pattern, List.copyOf(body)));
                Matcher header = SNIPPET_HEADER.matcher(line);
                if (!header.matches()) {
                    throw new IllegalStateException(file + ":" + (i + 1) + ": not a snippet header: " + line);
                }
                pattern = compile(header.group(1), file, i + 1);
                headerLine = i + 1;
                body = new ArrayList<>();
            } else {
                Matcher step = STEP.matcher(line);
                if (step.matches()) body.add(step.group(1));
            }
        }
        if (pattern != null) definitions.add(new Definition(file, headerLine, null, pattern, List.copyOf(body)));
        return definitions;
    }

    static List<Definition> javaSteps(String file, String text) {
        List<Definition> definitions = new ArrayList<>();
        Matcher step = JAVA_STEP.matcher(text);
        int from = 0;
        while (step.find(from)) {
            int line = (int) text.substring(0, step.start(1)).chars().filter(c -> c == '\n').count() + 1;
            definitions.add(new Definition(file, line, step.group(2),
                    compile(step.group(1).translateEscapes(), file, line), List.of()));
            // the next annotation of the same method, if any, starts inside this match
            from = text.indexOf('\n', step.start(1));
        }
        return definitions;
    }

    /** More than one occurrence of {@code method(}: the declaration is one. */
    private static boolean calledFromJava(String method, Iterable<String> sources) {
        Pattern call = Pattern.compile("\\b" + method + "\\(");
        long count = 0;
        for (String source : sources) count += call.matcher(source).results().count();
        return count > 1;
    }

    private static Pattern compile(String regex, String file, int line) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new IllegalStateException(file + ":" + line + ": not a regular expression: " + regex);
        }
    }

    private static List<Path> files(Path dir, String suffix) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(suffix)).sorted().toList();
        }
    }

    private static List<String> read(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    private static String relative(Path project, Path file) {
        return project.relativize(file).toString().replace('\\', '/');
    }
}
