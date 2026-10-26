package com.example.module.oreo.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Offline test-data tools. Run {@code mvn -Ptest-data process-test-classes} from the project root.
 * Maven compiles this class and refreshes test resources before running it. Select a command with
 * {@code -Dexec.args="sync-examples"}, or one of:
 * <pre>
 * check                 the offline checks of the data against the feature files and the code
 * sync-examples         writes the Examples of the product Outlines from ProductDatFiles
 * compact &lt;event&gt;|all   turns the recorded responses of the variants into patches on their base
 * suggest &lt;event&gt;       suggests the dynamicFields rules and the expectedBase from the recordings
 * case &lt;case ID&gt;        prints the merged data of one case
 * </pre>
 * A problem throws an exception so Maven fails without terminating its JVM.
 */
public class TestDataTool {

    private static final String USAGE =
            "usage: TestDataTool check | sync-examples | compact <event>|all | suggest <event> | case <case ID>";

    public static void main(String[] args) throws IOException, InterruptedException {
        String command = args.length == 0 ? "" : args[0];
        int problems;
        try {
            problems = switch (command) {
                case "check" -> args.length == 1 ? Checks.run() : usage();
                case "sync-examples" -> args.length == 1 ? SyncExamples.run() : usage();
                case "compact" -> args.length == 2 ? Compact.run(args[1].trim()) : usage();
                case "suggest" -> args.length == 2 ? Suggest.run(args[1].trim()) : usage();
                case "case" -> args.length == 2 ? Lookup.run(args[1].trim()) : usage();
                default -> usage();
            };
        } catch (IllegalStateException | IllegalArgumentException e) {
            // the project's errors say what is wrong and where; anything else keeps its stack trace
            System.out.println(e.getMessage());
            problems = 1;
        }
        if (problems != 0) {
            throw new IllegalStateException("Test-data command failed; see the diagnostics above");
        }
    }

    private static int usage() {
        System.out.println(USAGE);
        return 1;
    }

    /**
     * The case data is read from the copy of {@code src/test/resources/data} that {@code mvn test-compile} makes in
     * {@code target/test-classes}: stops when the copy is not the same, so that no answer is about old data. The
     * expected files are left out, they are read from {@code src}.
     */
    static void requireFreshData(Path project) throws IOException {
        Path source = project.resolve("src/test/resources/data");
        Path copy = project.resolve("target/test-classes/data");
        List<String> inSource = dataFiles(source);
        Set<String> names = new TreeSet<>(inSource);
        names.addAll(dataFiles(copy));
        List<String> stale = new ArrayList<>();
        boolean removed = false;
        for (String name : names) {
            Path a = source.resolve(name);
            Path b = copy.resolve(name);
            if (!Files.isRegularFile(a)) removed = true;
            if (!Files.isRegularFile(a) || !Files.isRegularFile(b) || Files.mismatch(a, b) != -1) stale.add(name);
        }
        if (!stale.isEmpty()) {
            throw new IllegalStateException("target/test-classes/data is not the same as src/test/resources/data ("
                    + stale.size() + " file(s), the first " + stale.get(0) + "): run mvn -Ptest-data process-test-classes"
                    + " again" + (removed ? "; a file removed from src stays in target until mvn clean" : ""));
        }
    }

    /** The {@code .json} and {@code .dat} files under {@code dir}, not under {@code api/expected}, with {@code /}. */
    private static List<String> dataFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .map(p -> dir.relativize(p).toString().replace('\\', '/'))
                    .filter(name -> name.endsWith(".json") || name.endsWith(".dat"))
                    .filter(name -> !name.startsWith("api/expected/"))
                    .sorted().toList();
        }
    }
}
