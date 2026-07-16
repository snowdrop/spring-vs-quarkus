///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.2
//DEPS org.aesh:aesh:3.16.5

package io.snowdrop.cartography.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.aesh.AeshRuntimeRunner;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

@CommandDefinition(name = "fetch-components", description = "Fetch Spring Boot starters and/or Quarkus extensions")
public class FetchComponents implements Command<CommandInvocation> {

    @Option(name = "target", shortName = 't', defaultValue = "both",
            description = "Framework(s) to fetch: spring, quarkus or both. Default: both")
    private String target;

    @Option(name = "format", shortName = 'f', defaultValue = "md",
            description = "Output format: md, csv. Default: md")
    private String format;

    @Option(shortName = 'o', description = "Write output to file (default: stdout)")
    private String outputFile;

    @Option(name = "boot-version", description = "Spring Boot version (default: latest stable)")
    private String bootVersion;

    @Option(shortName = 'h', name = "help", hasValue = false,
            overrideRequired = true, description = "Show help")
    private boolean help;

    static final String SPRING_CATALOG_URL = "https://start.spring.io";
    static final String SPRING_DEPS_URL = "https://start.spring.io/dependencies";
    static final String QUARKUS_REGISTRY_URL = "https://registry.quarkus.io/client/extensions/all";

    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        AeshRuntimeRunner.builder()
                .command(FetchComponents.class)
                .args(args)
                .execute();
    }

    @Override
    public CommandResult execute(CommandInvocation invocation) throws InterruptedException {
        try {
            if (help) {
                invocation.println(invocation.getHelpInfo("fetch-components"));
                return CommandResult.SUCCESS;
            }

            PrintWriter out = outputFile != null
                    ? new PrintWriter(new FileWriter(outputFile))
                    : new PrintWriter(System.out);

            switch (target) {
                case "spring" -> generateSpring(out, format, bootVersion);
                case "quarkus" -> generateQuarkus(out, format);
                case "both" -> generateBoth(out, format, bootVersion);
            }

            out.flush();
            if (outputFile != null) {
                out.close();
                System.err.println("Written to " + outputFile);
            }
            return CommandResult.SUCCESS;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return CommandResult.FAILURE;
        }
    }

    // --- Spring ---

    record SpringStarter(String category, String id, String name, String artifact,
                         String description, String refDoc, String guide) {}

    static List<SpringStarter> fetchSpringStarters(String bootVersion) throws Exception {
        System.err.println("Fetching Spring Initializr catalog...");
        JsonNode catalog = fetchJson(SPRING_CATALOG_URL, "application/json");

        if (bootVersion == null) {
            for (JsonNode v : catalog.path("bootVersion").path("values")) {
                if (!v.path("id").asText("").contains("SNAPSHOT")) {
                    bootVersion = v.path("id").asText();
                    break;
                }
            }
        }

        String depsUrl = SPRING_DEPS_URL + (bootVersion != null ? "?bootVersion=" + bootVersion : "");
        System.err.println("Fetching Maven coordinates (Boot " + bootVersion + ")...");
        JsonNode coords = fetchJson(depsUrl, "application/json");
        JsonNode depMap = coords.path("dependencies");

        var starters = new ArrayList<SpringStarter>();
        String finalBootVersion = bootVersion != null ? bootVersion : "4.1.0";

        for (JsonNode group : catalog.path("dependencies").path("values")) {
            String cat = group.path("name").asText();
            for (JsonNode dep : group.path("values")) {
                String id = dep.path("id").asText();
                String name = dep.path("name").asText();
                String desc = dep.path("description").asText("");

                String ref = extractLink(dep, "reference").replace("{bootVersion}", finalBootVersion);
                String guide = extractLink(dep, "guide");

                JsonNode coord = depMap.path(id);
                String artifact = coord.has("groupId")
                        ? coord.path("groupId").asText() + ":" + coord.path("artifactId").asText()
                        : "";

                starters.add(new SpringStarter(cat, id, name, artifact, desc, ref, guide));
            }
        }
        System.err.printf("Fetched %d Spring starters%n", starters.size());
        return starters;
    }

    static void generateSpring(PrintWriter out, String format, String bootVersion) throws Exception {
        List<SpringStarter> starters = fetchSpringStarters(bootVersion);

        if ("csv".equals(format)) {
            out.println("Category,Starter ID,Name,Maven Artifact,Description,Reference Doc,Guide");
            for (var s : starters) {
                out.printf("%s,%s,%s,%s,\"%s\",%s,%s%n",
                        csvEsc(s.category), csvEsc(s.id), csvEsc(s.name), csvEsc(s.artifact),
                        csvEsc(truncate(s.description, 200)), csvEsc(s.refDoc), csvEsc(s.guide));
            }
        } else {
            out.println("| Category | Starter ID | Name | Maven Artifact | Description | Reference Doc | Guide |");
            out.println("|----------|-----------|------|----------------|-------------|---------------|-------|");
            String prevCat = "";
            for (var s : starters) {
                String cat = s.category.equals(prevCat) ? "" : s.category;
                prevCat = s.category;
                String desc = truncate(s.description, 100);
                String art = s.artifact.isEmpty() ? "--" : "`" + s.artifact + "`";
                String ref = s.refDoc.isEmpty() ? "--" : "[ref](" + s.refDoc + ")";
                String guide = s.guide.isEmpty() ? "--" : "[guide](" + s.guide + ")";
                out.printf("| %s | %s | %s | %s | %s | %s | %s |%n",
                        cat, s.id, s.name, art, desc, ref, guide);
            }
        }
    }

    // --- Quarkus ---

    record QuarkusExtension(String category, String artifactId, String name, String artifact,
                            String description, String guide, String scm) {}

    static List<QuarkusExtension> fetchQuarkusExtensions() throws Exception {
        System.err.println("Fetching Quarkus registry...");
        JsonNode root = fetchJson(QUARKUS_REGISTRY_URL, null);
        JsonNode extensions = root.path("extensions");

        var byArtifactId = new LinkedHashMap<String, QuarkusExtension>();
        for (JsonNode ext : extensions) {
            String gav = ext.path("artifact").asText("");
            String artifactId = extractArtifactId(gav);
            String groupId = extractGroupId(gav);
            if (artifactId.isEmpty()) continue;

            String name = ext.path("name").asText("");
            String desc = ext.path("description").asText("");
            String guide = ext.path("metadata").path("guide").asText("");
            String scm = ext.path("metadata").path("scm-url").asText("");

            JsonNode categories = ext.path("metadata").path("categories");
            String cat = categories.isArray() && !categories.isEmpty()
                    ? categories.get(0).asText()
                    : "miscellaneous";

            String mavenArtifact = groupId + ":" + artifactId;
            var candidate = new QuarkusExtension(cat, artifactId, name, mavenArtifact, desc, guide, scm);

            byArtifactId.merge(artifactId, candidate, (existing, newer) ->
                    metadataScore(newer) > metadataScore(existing) ? newer : existing);
        }

        var result = new ArrayList<>(byArtifactId.values());
        System.err.printf("Fetched %d Quarkus extensions%n", result.size());
        return result;
    }

    static void generateQuarkus(PrintWriter out, String format) throws Exception {
        List<QuarkusExtension> extensions = fetchQuarkusExtensions();

        if ("csv".equals(format)) {
            out.println("Category,ArtifactId,Name,Maven Artifact,Description,Guide,SCM URL");
            for (var e : extensions) {
                out.printf("%s,%s,%s,%s,\"%s\",%s,%s%n",
                        csvEsc(e.category), csvEsc(e.artifactId), csvEsc(e.name),
                        csvEsc(e.artifact), csvEsc(truncate(e.description, 200)), csvEsc(e.guide), csvEsc(e.scm));
            }
        } else {
            out.println("| Category | ArtifactId | Name | Maven Artifact | Description | Guide | SCM |");
            out.println("|----------|-----------|------|----------------|-------------|-------|-----|");
            String prevCat = "";
            for (var e : extensions) {
                String cat = e.category.equals(prevCat) ? "" : e.category;
                prevCat = e.category;
                String desc = truncate(e.description, 100);
                String art = "`" + e.artifact + "`";
                String guide = e.guide.isEmpty() ? "--" : "[guide](" + e.guide + ")";
                String scm = e.scm.isEmpty() ? "--" : "[scm](" + e.scm + ")";
                out.printf("| %s | %s | %s | %s | %s | %s | %s |%n",
                        cat, e.artifactId, e.name, art, desc, guide, scm);
            }
        }
    }

    // --- Both (mapped) ---

    static final Map<String, List<String>> CATEGORY_MAPPING = Map.ofEntries(
            Map.entry("Web", List.of("web", "reactive", "rest")),
            Map.entry("Security", List.of("security", "authorization", "oidc")),
            Map.entry("SQL", List.of("data", "persistence")),
            Map.entry("NoSQL", List.of("data")),
            Map.entry("Messaging", List.of("messaging")),
            Map.entry("Observability", List.of("observability")),
            Map.entry("Testing", List.of("testing")),
            Map.entry("Template Engines", List.of("web")),
            Map.entry("I/O", List.of("integration", "serialization")),
            Map.entry("Ops", List.of("cloud", "containerization")),
            Map.entry("Spring Cloud", List.of("cloud")),
            Map.entry("Spring Cloud Config", List.of("configuration", "cloud")),
            Map.entry("Spring Cloud Discovery", List.of("cloud")),
            Map.entry("Spring Cloud Routing", List.of("cloud", "web")),
            Map.entry("Spring Cloud Circuit Breaker", List.of("cloud")),
            Map.entry("Spring Cloud Messaging", List.of("messaging", "cloud")),
            Map.entry("AI", List.of("ai", "artificial-intelligence"))
    );

    static void generateBoth(PrintWriter out, String format, String bootVersion) throws Exception {
        List<SpringStarter> springStarters = fetchSpringStarters(bootVersion);
        List<QuarkusExtension> quarkusExtensions = fetchQuarkusExtensions();

        Map<String, List<QuarkusExtension>> quarkusByCategory = new LinkedHashMap<>();
        for (var ext : quarkusExtensions) {
            quarkusByCategory.computeIfAbsent(ext.category.toLowerCase(), k -> new ArrayList<>()).add(ext);
        }

        Map<String, List<QuarkusExtension>> quarkusByKeyword = new LinkedHashMap<>();
        for (var ext : quarkusExtensions) {
            String key = ext.artifactId.replace("quarkus-", "").replace("-", " ");
            quarkusByKeyword.computeIfAbsent(key, k -> new ArrayList<>()).add(ext);
        }

        if ("csv".equals(format)) {
            out.println("Category,Spring Starter,Spring Artifact,Spring Description,Quarkus Extension,Quarkus Artifact,Quarkus Description,Quarkus SCM URL");
        } else {
            out.println("| Category | Spring Starter | Spring Artifact | Spring Description | Quarkus Extension | Quarkus Artifact | Quarkus Description | Quarkus SCM |");
            out.println("|----------|---------------|-----------------|-------------------|-------------------|------------------|---------------------|-------------|");
        }

        Set<String> matchedQuarkus = new HashSet<>();
        String prevCat = "";

        for (var s : springStarters) {
            QuarkusExtension match = findQuarkusMatch(s, quarkusByCategory, quarkusByKeyword, matchedQuarkus);
            if (match != null) matchedQuarkus.add(match.artifactId);

            String cat = s.category.equals(prevCat) ? "" : s.category;
            prevCat = s.category;

            if ("csv".equals(format)) {
                out.printf("%s,%s,%s,\"%s\",%s,%s,\"%s\",%s%n",
                        csvEsc(cat), csvEsc(s.name), csvEsc(s.artifact),
                        csvEsc(truncate(s.description, 120)),
                        match != null ? csvEsc(match.name) : "",
                        match != null ? csvEsc(match.artifact) : "",
                        match != null ? csvEsc(truncate(match.description, 120)) : "",
                        match != null ? csvEsc(match.scm) : "");
            } else {
                String sArt = s.artifact.isEmpty() ? "--" : "`" + s.artifact + "`";
                String qName = match != null ? match.name : "";
                String qArt = match != null ? "`" + match.artifact + "`" : "";
                String qDesc = match != null ? truncate(match.description, 80) : "";
                String qScm = match != null && !match.scm.isEmpty() ? "[scm](" + match.scm + ")" : "--";
                out.printf("| %s | %s | %s | %s | %s | %s | %s | %s |%n",
                        cat, s.name, sArt, truncate(s.description, 80), qName, qArt, qDesc, qScm);
            }
        }

        List<QuarkusExtension> unmatched = quarkusExtensions.stream()
                .filter(e -> !matchedQuarkus.contains(e.artifactId))
                .filter(e -> !e.artifactId.startsWith("quarkus-test"))
                .collect(Collectors.toList());

        if (!unmatched.isEmpty()) {
            System.err.printf("%d Quarkus extensions were not matched to a Spring starter%n", unmatched.size());
        }
    }

    static QuarkusExtension findQuarkusMatch(SpringStarter spring,
                                              Map<String, List<QuarkusExtension>> byCategory,
                                              Map<String, List<QuarkusExtension>> byKeyword,
                                              Set<String> alreadyMatched) {
        String springName = spring.name.toLowerCase().replace("spring ", "").replace("boot ", "");
        String springId = spring.id.toLowerCase();

        List<String> searchCategories = CATEGORY_MAPPING.getOrDefault(spring.category, List.of());
        List<QuarkusExtension> candidates = new ArrayList<>();
        for (String qCat : searchCategories) {
            var exts = byCategory.get(qCat);
            if (exts != null) candidates.addAll(exts);
        }

        for (var ext : candidates) {
            if (alreadyMatched.contains(ext.artifactId)) continue;
            String qName = ext.name.toLowerCase();
            String qArt = ext.artifactId.replace("quarkus-", "").replace("-", " ");

            if (nameMatches(springName, springId, qName, qArt)) {
                return ext;
            }
        }

        for (var entry : byKeyword.entrySet()) {
            String key = entry.getKey();
            if (springName.contains(key) || key.contains(springName) ||
                springId.replace("-", " ").contains(key) || key.contains(springId.replace("-", " "))) {
                for (var ext : entry.getValue()) {
                    if (!alreadyMatched.contains(ext.artifactId)) return ext;
                }
            }
        }

        return null;
    }

    static boolean nameMatches(String springName, String springId, String qName, String qArtKey) {
        if (springName.length() < 3) return false;

        if (qName.contains(springName) || springName.contains(qName.replace("quarkus ", ""))) return true;

        String[] springWords = springName.split("\\s+");
        for (String word : springWords) {
            if (word.length() >= 4 && (qName.contains(word) || qArtKey.contains(word))) return true;
        }

        String[] idWords = springId.split("-");
        for (String word : idWords) {
            if (word.length() >= 4 && (qArtKey.contains(word) || qName.contains(word))) return true;
        }

        return false;
    }

    // --- Utilities ---

    static int metadataScore(QuarkusExtension ext) {
        int score = 0;
        if (!ext.scm.isEmpty()) score++;
        if (!ext.guide.isEmpty()) score++;
        if (!ext.description.isEmpty()) score++;
        return score;
    }

    static JsonNode fetchJson(String url, String accept) throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder(URI.create(url)).GET();
        if (accept != null) builder.header("Accept", accept);
        var response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return MAPPER.readTree(response.body());
    }

    static String extractLink(JsonNode dep, String linkName) {
        JsonNode link = dep.path("_links").path(linkName);
        if (link.isMissingNode()) return "";
        if (link.isArray()) return link.isEmpty() ? "" : link.get(0).path("href").asText("");
        return link.path("href").asText("");
    }

    static String extractArtifactId(String gav) {
        if (gav == null || gav.isEmpty()) return "";
        String[] parts = gav.split(":");
        return parts.length >= 2 ? parts[1] : "";
    }

    static String extractGroupId(String gav) {
        if (gav == null || gav.isEmpty()) return "";
        String[] parts = gav.split(":");
        return parts.length >= 1 ? parts[0] : "";
    }

    static String truncate(String s, int max) {
        if (s == null) return "";
        s = s.replaceAll("[\\r\\n]+", " ").replaceAll("\\s{2,}", " ").strip();
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    static String csvEsc(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

}
