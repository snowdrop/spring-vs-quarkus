package io.snowdrop.cartography.service;

import io.snowdrop.cartography.model.Capability;
import io.snowdrop.cartography.model.ComponentType;
import io.snowdrop.cartography.model.FrameworkEntry;
import io.snowdrop.cartography.repository.CapabilityRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class ExportService {

    @Inject
    CapabilityRepository repository;

    public String exportCsv() {
        var sb = new StringBuilder();
        sb.append("Spring project,Github repo,Spring sub-projects,Quarkus project,Quarkus Github repo,Quarkus sub-projects,Spring Boot starter,Quarkus extension,Spring supported,Quarkus supported\n");

        List<Capability> capabilities = repository.findAllOrdered();
        for (Capability c : capabilities) {
            List<FrameworkEntry> springEntries = c.getSpringEntries();
            List<FrameworkEntry> quarkusEntries = c.getQuarkusEntries();

            FrameworkEntry springProject = springEntries.isEmpty() ? null : springEntries.get(0);
            FrameworkEntry quarkusProject = quarkusEntries.isEmpty() ? null : quarkusEntries.get(0);

            List<FrameworkEntry> springSubs = springEntries.stream()
                    .filter(e -> e.getType() == ComponentType.STARTER)
                    .filter(e -> e != springProject)
                    .toList();
            List<FrameworkEntry> quarkusSubs = quarkusEntries.stream()
                    .filter(e -> e.getType() == ComponentType.EXTENSION)
                    .filter(e -> e != quarkusProject)
                    .toList();
            List<FrameworkEntry> starters = springEntries.stream()
                    .filter(e -> e.getType() == ComponentType.STARTER)
                    .toList();
            List<FrameworkEntry> extensions = quarkusEntries.stream()
                    .filter(e -> e.getType() == ComponentType.EXTENSION)
                    .toList();

            int rows = Math.max(1, Math.max(
                    Math.max(springSubs.size(), quarkusSubs.size()),
                    Math.max(starters.size(), extensions.size())));

            for (int i = 0; i < rows; i++) {
                boolean firstRow = (i == 0);

                if (firstRow) {
                    sb.append(hyperlink(springProject));
                    sb.append(',');
                    sb.append(githubLink(springProject));
                } else {
                    sb.append(',');
                }
                sb.append(',');
                sb.append(i < springSubs.size() ? hyperlink(springSubs.get(i)) : "");
                sb.append(',');
                if (firstRow) {
                    sb.append(hyperlink(quarkusProject));
                    sb.append(',');
                    sb.append(githubLink(quarkusProject));
                } else {
                    sb.append(',');
                }
                sb.append(',');
                sb.append(i < quarkusSubs.size() ? hyperlink(quarkusSubs.get(i)) : "");
                sb.append(',');
                sb.append(i < starters.size() ? hyperlink(starters.get(i)) : "");
                sb.append(',');
                sb.append(i < extensions.size() ? hyperlink(extensions.get(i)) : "");
                sb.append(',');
                sb.append(c.isSpringSupported() ? "YES" : "NO");
                sb.append(',');
                sb.append(c.isQuarkusSupported() ? "YES" : "NO");
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private String hyperlink(FrameworkEntry entry) {
        if (entry == null || entry.getName() == null) return "";
        if (entry.getUrl() != null) {
            return "\"=HYPERLINK(\"\"" + entry.getUrl() + "\"\",\"\"" + escapeCsv(entry.getName()) + "\"\")\"";
        }
        return csvField(entry.getName());
    }

    private String githubLink(FrameworkEntry entry) {
        if (entry == null || entry.getGithub() == null) return "";
        String repoName = extractRepoName(entry.getGithub());
        return "\"=HYPERLINK(\"\"" + entry.getGithub() + "\"\",\"\"" + escapeCsv(repoName) + "\"\")\"";
    }

    private String extractRepoName(String githubUrl) {
        if (githubUrl == null) return "";
        String path = githubUrl.replaceFirst("https?://github\\.com/", "");
        if (path.contains("/tree/")) {
            path = path.substring(0, path.indexOf("/tree/"));
        }
        int slash = path.indexOf('/');
        if (slash >= 0) {
            return path.substring(slash + 1);
        }
        return path;
    }

    private String csvField(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String escapeCsv(String value) {
        return value == null ? "" : value.replace("\"", "\"\"");
    }

    public String exportMarkdown() {
        var sb = new StringBuilder();
        List<Capability> capabilities = repository.findAllOrdered();
        long total = capabilities.size();
        long both = capabilities.stream().filter(c -> c.isSpringSupported() && c.isQuarkusSupported()).count();
        long springOnly = capabilities.stream().filter(c -> c.isSpringSupported() && !c.isQuarkusSupported()).count();
        long quarkusOnly = capabilities.stream().filter(c -> !c.isSpringSupported() && c.isQuarkusSupported()).count();

        sb.append("# Spring vs Quarkus Capability Comparison\n\n");
        sb.append("## Summary\n\n");
        sb.append("| Metric | Count |\n");
        sb.append("|--------|-------|\n");
        sb.append("| Total capabilities | ").append(total).append(" |\n");
        sb.append("| Both supported | ").append(both).append(" |\n");
        sb.append("| Spring only | ").append(springOnly).append(" |\n");
        sb.append("| Quarkus only | ").append(quarkusOnly).append(" |\n\n");

        sb.append("## Comparison Table\n\n");
        sb.append("| Category | Spring | Quarkus | Spring Supported | Quarkus Supported |\n");
        sb.append("|----------|--------|---------|:----------------:|:-----------------:|\n");
        for (Capability c : capabilities) {
            FrameworkEntry springMain = c.getSpringEntries().isEmpty() ? null : c.getSpringEntries().get(0);
            FrameworkEntry quarkusMain = c.getQuarkusEntries().isEmpty() ? null : c.getQuarkusEntries().get(0);
            sb.append("| ").append(c.getCategory());
            sb.append(" | ").append(mdLink(springMain));
            sb.append(" | ").append(mdLink(quarkusMain));
            sb.append(" | ").append(c.isSpringSupported() ? "Yes" : "**No**");
            sb.append(" | ").append(c.isQuarkusSupported() ? "Yes" : "**No**");
            sb.append(" |\n");
        }

        sb.append("\n## Details\n\n");
        for (Capability c : capabilities) {
            sb.append("### ").append(c.getCategory()).append("\n\n");
            if (c.getDescription() != null) {
                sb.append(c.getDescription()).append("\n\n");
            }
            if (!c.getEntries().isEmpty()) {
                sb.append("| Framework | Feature | Type |\n");
                sb.append("|-----------|---------|------|\n");
                for (FrameworkEntry e : c.getEntries()) {
                    sb.append("| ").append(e.getFramework());
                    sb.append(" | ").append(mdEntryLink(e));
                    sb.append(" | ").append(e.getType() != null ? e.getType() : "");
                    sb.append(" |\n");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String mdLink(FrameworkEntry entry) {
        if (entry == null || entry.getName() == null) return "---";
        if (entry.getUrl() != null) return "[" + entry.getName() + "](" + entry.getUrl() + ")";
        return entry.getName();
    }

    private String mdEntryLink(FrameworkEntry e) {
        if (e == null || e.getName() == null) return "---";
        var s = new StringBuilder();
        if (e.getUrl() != null) {
            s.append("[").append(e.getName()).append("](").append(e.getUrl()).append(")");
        } else {
            s.append(e.getName());
        }
        if (e.getGithub() != null) {
            s.append(" ([GitHub](").append(e.getGithub()).append("))");
        }
        return s.toString();
    }
}
