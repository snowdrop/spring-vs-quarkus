package io.snowdrop.cartography.service;

import io.snowdrop.cartography.model.Capability;
import io.snowdrop.cartography.model.FrameworkEntry;
import io.snowdrop.cartography.store.RegistryStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class MarkdownService {

    @Inject
    RegistryStore store;

    public String exportMarkdown() {
        var sb = new StringBuilder();
        List<Capability> capabilities = store.findAllOrdered();
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

        sb.append("## Capabilities\n\n");
        sb.append("| Capability | Description | Tags | Framework | Name | Doc URL | SCM URL | Type | Since | Review By | Review Date | Quarkus Status | Status Comment |\n");
        sb.append("|----------|-------------|------|-----------|------|---------|---------|------|-------|-----------|-------------|----------------|----------------|\n");
        for (Capability c : capabilities) {
            if (c.getEntries().isEmpty()) {
                sb.append("| ").append(c.getCategory());
                sb.append(" | ").append(c.getDescription() != null ? c.getDescription() : "");
                sb.append(" | ").append(c.getTags() != null ? c.getTags() : "");
                sb.append(" | | | | | | ");
                sb.append(" | ").append(c.getReviewBy() != null ? c.getReviewBy() : "");
                sb.append(" | ").append(c.getReviewDate() != null ? c.getReviewDate() : "");
                sb.append(" | ").append(c.getQuarkusStatus() != null ? c.getQuarkusStatus() : "");
                sb.append(" | ").append(c.getStatusComment() != null ? c.getStatusComment() : "");
                sb.append(" |\n");
            } else {
                boolean first = true;
                var sorted = c.getEntries().stream()
                        .sorted(Comparator.comparingInt(e -> e.getFramework() != null ? e.getFramework().ordinal() : Integer.MAX_VALUE))
                        .toList();
                for (FrameworkEntry e : sorted) {
                    sb.append("| ").append(c.getCategory());
                    sb.append(" | ").append(c.getDescription() != null ? c.getDescription() : "");
                    sb.append(" | ").append(c.getTags() != null ? c.getTags() : "");
                    sb.append(" | ").append(e.getFramework() != null ? e.getFramework() : "");
                    sb.append(" | ").append(e.getName() != null ? e.getName() : "");
                    sb.append(" | ").append(e.getDoc() != null ? e.getDoc() : "");
                    sb.append(" | ").append(e.getScm() != null ? e.getScm() : "");
                    sb.append(" | ").append(e.getType() != null ? e.getType() : "");
                    sb.append(" | ").append(e.getSince() != null ? e.getSince() : "");
                    if (first) {
                        sb.append(" | ").append(c.getReviewBy() != null ? c.getReviewBy() : "");
                        sb.append(" | ").append(c.getReviewDate() != null ? c.getReviewDate() : "");
                        sb.append(" | ").append(c.getQuarkusStatus() != null ? c.getQuarkusStatus() : "");
                        sb.append(" | ").append(c.getStatusComment() != null ? c.getStatusComment() : "");
                        first = false;
                    } else {
                        sb.append(" | | | | ");
                    }
                    sb.append(" |\n");
                }
            }
        }
        return sb.toString();
    }
}
