package com.example.module.oreo.testdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Rewrites the expected files of an event so that a variant holds only its patch on the base case its data
 * declares ({@code expectedBase}), and a case without a base its full response:
 *
 * <ol>
 *   <li>every file is resolved to its full form first, from the files as they are;</li>
 *   <li>a case with base {@code B} gets the patch from {@code B}'s full form to its own, written only when its
 *       text is shorter than the full form's;</li>
 *   <li>in a file kept in full, each part the case declares shared ({@code sharedFragments}) becomes a reference to
 *       {@code _shared/<name>.json} plus the keys that differ from it; a part not written yet is taken from the first
 *       case that has it ({@link Shared});</li>
 *   <li>any error (a base without a file, a base with a base of its own, a patch that does not apply, case data
 *       that does not load) stops the compaction before a file is written;</li>
 *   <li>a file whose text does not change is not rewritten.</li>
 * </ol>
 *
 * Recording always writes full files; this runs afterwards, in one JVM ({@code TestDataTool compact}).
 */
public final class ExpectedCompaction {

    /** One file: its text before and after, and its form after ({@code full (no base)}, {@code patch on ...}). */
    public record Entry(CaseId id, String before, String after, String note) {
        public boolean changed() {
            return !before.equals(after);
        }
    }

    /** The planned files of one event and the errors that stop it. */
    public record Plan(String eventFile, List<Entry> entries, List<String> errors) {}

    /** One shared part: its text before ({@code ""} when new) and after ({@code null} when it is deleted). */
    public record SharedEntry(String name, String before, String after) {
        public boolean changed() {
            return !before.equals(after == null ? "" : after);
        }
    }

    /**
     * The shared parts of one compaction, across its events. {@link #fromDisk}: the files in {@code _shared/} are
     * kept, a missing one is taken from the first case that has the part. {@link #rebuild} ({@code compact all}): every
     * part is taken again from the first case that has it, and a file no case uses any more is deleted.
     */
    public static final class Shared {
        private final SharedFragments disk;
        private final boolean rebuild;
        private final Map<String, ObjectNode> parts = new LinkedHashMap<>();
        private final Set<String> used = new TreeSet<>();

        private Shared(SharedFragments disk, boolean rebuild) {
            this.disk = disk;
            this.rebuild = rebuild;
        }

        public static Shared fromDisk(ExpectedResponses files) {
            return new Shared(files.shared(), false);
        }

        public static Shared rebuild(ExpectedResponses files) {
            return new Shared(files.shared(), true);
        }

        /** The part to refer to, taking {@code candidate} when there is none yet. */
        ObjectNode part(String name, ObjectNode candidate) {
            used.add(name);
            ObjectNode part = parts.get(name);
            if (part == null && !rebuild) part = disk.read(name).orElse(null);
            if (part == null) part = candidate.deepCopy();
            parts.put(name, part);
            return part;
        }

        /** The shared files that change: new or different parts and, when rebuilding, parts no case uses. */
        public List<SharedEntry> entries() {
            List<SharedEntry> out = new ArrayList<>();
            for (Map.Entry<String, ObjectNode> part : parts.entrySet()) {
                String before = text(part.getKey());
                SharedEntry entry = new SharedEntry(part.getKey(), before, Json.pretty(part.getValue()));
                if (entry.changed()) out.add(entry);
            }
            if (rebuild) {
                for (String name : disk.names()) {
                    if (!used.contains(name)) out.add(new SharedEntry(name, text(name), null));
                }
            }
            return out;
        }

        private String text(String name) {
            Optional<ObjectNode> onDisk = disk.read(name);
            return onDisk.map(Json::pretty).orElse("");
        }
    }

    private ExpectedCompaction() {}

    /**
     * Plans the compaction of {@code <root>/<eventFile>/}; writes nothing.
     *
     * @param declaredBase the base case ID a case declares, {@code null} for none (e.g. {@code CaseData::expectedBase})
     */
    public static Plan plan(ExpectedResponses files, String eventFile, Function<CaseId, String> declaredBase) {
        return plan(files, eventFile, declaredBase, id -> Map.of(), Shared.fromDisk(files));
    }

    /**
     * Plans the compaction of {@code <root>/<eventFile>/}; writes nothing (the shared parts it decides go to
     * {@code shared}, written by {@link #write(ExpectedResponses, Shared)}).
     *
     * @param declaredBase      the base case ID a case declares, {@code null} for none
     * @param declaredFragments response path -> shared part name of a case (e.g. {@code CaseData::sharedFragments})
     */
    public static Plan plan(ExpectedResponses files, String eventFile, Function<CaseId, String> declaredBase,
                            Function<CaseId, Map<String, String>> declaredFragments, Shared shared) {
        List<String> errors = new ArrayList<>();
        List<CaseId> ids;
        try {
            ids = files.cases(eventFile);
        } catch (RuntimeException e) {
            return new Plan(eventFile, List.of(), List.of(e.getMessage()));
        }
        Map<String, String> before = new LinkedHashMap<>();
        Map<String, JsonNode> full = new HashMap<>();
        Map<String, String> bases = new HashMap<>();
        Map<String, Map<String, String>> fragments = new HashMap<>();
        for (CaseId id : ids) {
            before.put(id.raw(), files.read(id).orElseThrow());
            try {
                full.put(id.raw(), Json.parse(files.resolve(id).orElseThrow().json()));
            } catch (RuntimeException e) {
                errors.add(id.raw() + ": " + e.getMessage());
            }
            try {
                bases.put(id.raw(), declaredBase.apply(id));
                fragments.put(id.raw(), declaredFragments.apply(id));
            } catch (RuntimeException e) {
                errors.add(id.raw() + ": its case data does not load: " + e.getMessage());
            }
        }

        List<Entry> entries = new ArrayList<>();
        for (CaseId id : ids) {
            JsonNode own = full.get(id.raw());
            if (own == null || !bases.containsKey(id.raw())) continue; // error listed above
            String old = before.get(id.raw());
            String base = bases.get(id.raw());
            if (base == null || base.equals(id.raw())) {
                entries.add(full(id, old, own, "full (no base)", fragments.get(id.raw()), shared, errors));
                continue;
            }
            String problem = baseProblem(files, eventFile, base, before, full, bases);
            if (problem != null) {
                errors.add(id.raw() + ": its base " + base + " " + problem);
                continue;
            }
            ArrayNode ops = JsonPatch.diff(full.get(base), own);
            ObjectNode patch = Json.MAPPER.createObjectNode();
            patch.put("_base", base);
            patch.set("_patch", ops);
            String patchText = Json.pretty(patch);
            String fullText = Json.pretty(withReferences(own, fragments.get(id.raw()), shared, null, new ArrayList<>()));
            if (patchText.length() < fullText.length()) {
                entries.add(new Entry(id, old, patchText, "patch on " + base + ", " + ops.size() + " ops"));
            } else {
                entries.add(full(id, old, own, "full (patch not shorter)", fragments.get(id.raw()), shared, errors));
            }
        }
        return new Plan(eventFile, List.copyOf(entries), List.copyOf(errors));
    }

    /** The entry of a file kept in full: its shared parts as references, noted. */
    private static Entry full(CaseId id, String old, JsonNode own, String form, Map<String, String> fragments,
                              Shared shared, List<String> errors) {
        List<String> notes = new ArrayList<>();
        JsonNode out;
        try {
            out = withReferences(own, fragments, shared, notes, errors);
        } catch (RuntimeException e) {
            errors.add(id.raw() + ": " + e.getMessage());
            out = own;
        }
        return new Entry(id, old, Json.pretty(out), notes.isEmpty() ? form : form + "; " + String.join("; ", notes));
    }

    /**
     * {@code own} with each declared part that is an object replaced by a reference to its shared part. A part that
     * lacks a key of the shared part cannot be a reference (a reference cannot remove a key): it stays in full, noted.
     * {@code notes} {@code null}: only the text is wanted, nothing is recorded in {@code shared}.
     */
    private static JsonNode withReferences(JsonNode own, Map<String, String> fragments, Shared shared,
                                           List<String> notes, List<String> errors) {
        JsonNode out = own.deepCopy();
        if (fragments == null) return out;
        for (Map.Entry<String, String> fragment : fragments.entrySet()) {
            String[] keys = fragment.getKey().split("\\.");
            JsonNode parent = out;
            for (int i = 0; i < keys.length - 1 && parent != null; i++) parent = parent.get(keys[i]);
            String leaf = keys[keys.length - 1];
            if (!(parent instanceof ObjectNode holder) || !(holder.get(leaf) instanceof ObjectNode part)
                    || SharedFragments.isReference(part)) {
                continue;
            }
            String name = fragment.getValue();
            ObjectNode sharedPart = notes == null ? peek(shared, name, part) : shared.part(name, part);
            ObjectNode ref = SharedFragments.reference(name, sharedPart, part);
            if (ref == null) {
                if (notes != null) {
                    notes.add(fragment.getKey() + " in full (it lacks a key of " + name + ")");
                }
                continue;
            }
            holder.set(leaf, ref);
            if (notes != null) {
                int differ = ref.size() - 1;
                notes.add(fragment.getKey() + " -> " + name + (differ == 0 ? "" : " (" + differ + " key(s) differ)"));
            }
        }
        return out;
    }

    /** The part {@code shared} would give, without recording anything. */
    private static ObjectNode peek(Shared shared, String name, ObjectNode candidate) {
        ObjectNode part = shared.parts.get(name);
        if (part == null && !shared.rebuild) part = shared.disk.read(name).orElse(null);
        return part != null ? part : candidate;
    }

    /** Why {@code base} cannot be a base, or {@code null}; a base whose own file failed is reported for that file. */
    private static String baseProblem(ExpectedResponses files, String eventFile, String base,
                                      Map<String, String> before, Map<String, JsonNode> full,
                                      Map<String, String> bases) {
        CaseId baseId;
        try {
            baseId = CaseId.parse(base);
        } catch (IllegalArgumentException e) {
            return "is not a case ID: " + e.getMessage();
        }
        if (!baseId.eventFile().equals(eventFile) || !baseId.channel().equals("API")) {
            return "is in another folder: a base is an API case of " + eventFile;
        }
        if (!before.containsKey(base)) {
            return "has no file " + files.fileFor(baseId) + " (record it first)";
        }
        if (!full.containsKey(base) || !bases.containsKey(base)) {
            return "has a problem of its own (listed for it)";
        }
        String baseOfBase = bases.get(base);
        if (baseOfBase != null && !baseOfBase.equals(base)) {
            return "declares a base of its own (" + baseOfBase + "): a base is a full file";
        }
        return null;
    }

    /** Writes the shared parts that change (new, different, or no longer used when rebuilding). */
    public static void write(ExpectedResponses files, Shared shared) {
        for (SharedEntry entry : shared.entries()) {
            if (entry.after() == null) {
                files.shared().delete(entry.name());
            } else {
                files.shared().write(entry.name(), entry.after());
            }
        }
    }

    /** Writes the changed files of {@code plan}; when it has errors, writes nothing and lists them. */
    public static void write(ExpectedResponses files, Plan plan) {
        if (!plan.errors().isEmpty()) {
            throw new IllegalStateException("[ExpectedCompaction] " + plan.eventFile() + ": nothing written, "
                    + plan.errors().size() + " problem(s):\n  " + String.join("\n  ", plan.errors()));
        }
        for (Entry entry : plan.entries()) {
            if (entry.changed()) files.write(entry.id(), entry.after());
        }
    }
}
