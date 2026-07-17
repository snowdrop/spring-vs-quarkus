package io.snowdrop.cartography.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.snowdrop.cartography.model.Capability;
import io.snowdrop.cartography.model.ComponentType;
import io.snowdrop.cartography.model.Framework;
import io.snowdrop.cartography.model.FrameworkEntry;
import io.snowdrop.cartography.repository.CapabilityRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jboss.logging.Logger;

@ApplicationScoped
public class RegistryEnrichmentService {

    private static final Logger LOG = Logger.getLogger(RegistryEnrichmentService.class);
    private static final String REGISTRY_URL = "https://registry.quarkus.io/client/extensions/all";
    private static final String MAVEN_SEARCH_URL = "https://search.maven.org/solrsearch/select";
    private static final String GITHUB_SEARCH_URL = "https://api.github.com/search/repositories";
    private static final DateTimeFormatter SINCE_FORMAT =
            DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH).withZone(ZoneId.of("UTC"));

    @Inject
    CapabilityRepository repository;

    @Inject
    YamlDataService yamlDataService;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Transactional
    public EnrichmentResult enrich() {
        var result = new EnrichmentResult();

        Map<String, JsonNode> registryByArtifact = fetchRegistry();
        if (registryByArtifact.isEmpty()) {
            result.errors.add("Failed to fetch Quarkus registry");
            return result;
        }
        result.registrySize = registryByArtifact.size();

        List<Capability> capabilities = repository.findAllOrdered();
        for (Capability cap : capabilities) {
            for (FrameworkEntry entry : cap.getEntries()) {
                if (entry.getFramework() != Framework.Quarkus) continue;

                JsonNode ext = matchExtension(entry, registryByArtifact);
                if (ext == null) {
                    entry.setComment("Not found in Quarkus registry");
                    result.notFound.add(entry.getName());
                    continue;
                }

                String artifactGav = ext.path("artifact").asText("");
                String artifactId = extractArtifactId(artifactGav);
                String groupId = extractGroupId(artifactGav);

                entry.setName(artifactId);
                entry.setType(ComponentType.EXTENSION);

                String guide = ext.path("metadata").path("guide").asText(null);
                if (guide != null && !guide.isBlank()) {
                    entry.setDoc(guide);
                }

                String scmUrl = ext.path("metadata").path("scm-url").asText(null);
                if (scmUrl != null && !scmUrl.isBlank()) {
                    entry.setScm(scmUrl);
                }

                String description = ext.path("description").asText(null);
                if (description != null && !description.isBlank()) {
                    entry.setDescription(description);
                }

                JsonNode categories = ext.path("metadata").path("categories");
                var comments = new ArrayList<String>();
                if (categories.isArray() && categories.size() > 0) {
                    String firstCategory = categories.get(0).asText();

                    if (categories.size() > 1) {
                        var extraCategories = new ArrayList<String>();
                        for (int i = 1; i < categories.size(); i++) {
                            extraCategories.add(categories.get(i).asText());
                        }
                        String existingTags = cap.getTags();
                        String newTags = String.join(", ", extraCategories);
                        cap.setTags(existingTags != null && !existingTags.isBlank()
                                ? existingTags + ", " + newTags : newTags);
                        comments.add("Registry categories: " + firstCategory + ", " + String.join(", ", extraCategories));
                    }
                }

                String since = fetchFirstReleaseDate(groupId, artifactId);
                if (since != null) {
                    entry.setSince(since);
                } else {
                    comments.add("First release date not found on Maven Central");
                }

                entry.setComment(comments.isEmpty() ? null : String.join("; ", comments));
                result.enriched.add(artifactId);
            }
        }

        enrichSpringEntries(capabilities, result);

        yamlDataService.saveToYaml();
        return result;
    }

    private void enrichSpringEntries(List<Capability> capabilities, EnrichmentResult result) {
        for (Capability cap : capabilities) {
            for (FrameworkEntry entry : cap.getEntries()) {
                if (entry.getFramework() != Framework.Spring) continue;

                String name = entry.getName();
                if (name == null || name.isBlank()) continue;

                String searchTerm = buildSpringSearchTerm(name);
                JsonNode repo = searchSpringRepo(searchTerm);

                if (repo == null) {
                    if (entry.getComment() == null) {
                        entry.setComment("Not found in spring-projects GitHub org");
                    }
                    result.notFound.add(name);
                    continue;
                }

                String htmlUrl = repo.path("html_url").asText(null);
                if (htmlUrl != null && entry.getScm() == null) {
                    entry.setScm(htmlUrl);
                }

                String repoDesc = repo.path("description").asText(null);
                if (repoDesc != null && !repoDesc.isBlank() && entry.getDescription() == null) {
                    entry.setDescription(repoDesc);
                }

                JsonNode topics = repo.path("topics");
                if (topics.isArray() && topics.size() > 0) {
                    var topicList = new ArrayList<String>();
                    for (JsonNode t : topics) {
                        topicList.add(t.asText());
                    }
                    String existingTags = cap.getTags();
                    String newTags = String.join(", ", topicList);
                    if (existingTags == null || existingTags.isBlank()) {
                        cap.setTags(newTags);
                    } else if (!existingTags.contains(topicList.get(0))) {
                        cap.setTags(existingTags + ", " + newTags);
                    }
                }

                String createdAt = repo.path("created_at").asText(null);
                if (createdAt != null && entry.getSince() == null) {
                    try {
                        Instant created = Instant.parse(createdAt);
                        entry.setSince(SINCE_FORMAT.format(created));
                    } catch (Exception e) {
                        LOG.warnf("Failed to parse created_at: %s", createdAt);
                    }
                }

                result.enriched.add(name);
            }
        }
    }

    private String buildSpringSearchTerm(String entryName) {
        String term = entryName;
        if (term.startsWith("spring-boot-starter-")) {
            term = "spring-" + term.substring("spring-boot-starter-".length());
        }
        term = term.replaceAll("[^a-zA-Z0-9 -]", "").trim();
        return term;
    }

    private JsonNode searchSpringRepo(String searchTerm) {
        try {
            String url = GITHUB_SEARCH_URL + "?q=org:spring-projects+" + searchTerm.replace(" ", "+");
            var request = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "application/vnd.github.v3+json")
                    .GET().build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());
            JsonNode items = root.path("items");
            if (items.isArray() && items.size() > 0) {
                return items.get(0);
            }
            return null;
        } catch (Exception e) {
            LOG.warnf("GitHub search failed for '%s': %s", searchTerm, e.getMessage());
            return null;
        }
    }

    private Map<String, JsonNode> fetchRegistry() {
        try {
            var request = HttpRequest.newBuilder(URI.create(REGISTRY_URL)).GET().build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());
            JsonNode extensions = root.path("extensions");

            var map = new HashMap<String, JsonNode>();
            for (JsonNode ext : extensions) {
                String gav = ext.path("artifact").asText("");
                String artifactId = extractArtifactId(gav);
                if (!artifactId.isEmpty()) {
                    map.putIfAbsent(artifactId, ext);
                }
                String displayName = ext.path("name").asText("").toLowerCase(Locale.ROOT);
                if (!displayName.isEmpty()) {
                    map.putIfAbsent("name:" + displayName, ext);
                }
            }
            LOG.infof("Fetched %d extensions from Quarkus registry", map.size());
            return map;
        } catch (Exception e) {
            LOG.error("Failed to fetch Quarkus registry", e);
            return Map.of();
        }
    }

    private JsonNode matchExtension(FrameworkEntry entry, Map<String, JsonNode> registry) {
        String name = entry.getName();
        if (name == null) return null;

        JsonNode match = registry.get(name);
        if (match != null) return match;

        String normalized = name.toLowerCase(Locale.ROOT)
                .replace(" ", "-")
                .replaceAll("[^a-z0-9-]", "");
        match = registry.get(normalized);
        if (match != null) return match;

        if (!normalized.startsWith("quarkus-")) {
            match = registry.get("quarkus-" + normalized);
            if (match != null) return match;
        }

        match = registry.get("name:" + name.toLowerCase(Locale.ROOT));
        if (match != null) return match;

        for (var regEntry : registry.entrySet()) {
            if (regEntry.getKey().startsWith("name:")) continue;
            if (regEntry.getKey().contains(normalized) || normalized.contains(regEntry.getKey())) {
                return regEntry.getValue();
            }
        }

        return null;
    }

    String fetchFirstReleaseDate(String groupId, String artifactId) {
        try {
            String countUrl = MAVEN_SEARCH_URL + "?q=g:" + groupId + "+AND+a:" + artifactId
                    + "&core=gav&rows=0&wt=json";
            var countReq = HttpRequest.newBuilder(URI.create(countUrl)).GET().build();
            var countResp = httpClient.send(countReq, HttpResponse.BodyHandlers.ofString());
            JsonNode countRoot = mapper.readTree(countResp.body());
            int numFound = countRoot.path("response").path("numFound").asInt(0);
            if (numFound == 0) return null;

            String fetchUrl = MAVEN_SEARCH_URL + "?q=g:" + groupId + "+AND+a:" + artifactId
                    + "&core=gav&rows=1&wt=json&start=" + (numFound - 1);
            var fetchReq = HttpRequest.newBuilder(URI.create(fetchUrl)).GET().build();
            var fetchResp = httpClient.send(fetchReq, HttpResponse.BodyHandlers.ofString());
            JsonNode fetchRoot = mapper.readTree(fetchResp.body());
            JsonNode docs = fetchRoot.path("response").path("docs");
            if (docs.isEmpty()) return null;

            long timestamp = docs.get(0).path("timestamp").asLong(0);
            if (timestamp == 0) return null;

            return SINCE_FORMAT.format(Instant.ofEpochMilli(timestamp));
        } catch (Exception e) {
            LOG.warnf("Failed to fetch first release date for %s:%s: %s", groupId, artifactId, e.getMessage());
            return null;
        }
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

    public static class EnrichmentResult {
        public int registrySize;
        public List<String> enriched = new ArrayList<>();
        public List<String> notFound = new ArrayList<>();
        public List<String> errors = new ArrayList<>();
    }
}
