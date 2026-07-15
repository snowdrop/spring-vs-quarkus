package io.snowdrop.cartography.service;

import io.snowdrop.cartography.model.Capability;
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
        sb.append("Category,Framework,Name,Doc URL,SCM URL,Description,Type,Since\n");

        List<Capability> capabilities = repository.findAllOrdered();
        for (Capability c : capabilities) {
            if (c.getEntries().isEmpty()) {
                sb.append(csvField(c.getCategory()));
                sb.append(",,,,,,,\n");
            } else {
                for (FrameworkEntry e : c.getEntries()) {
                    sb.append(csvField(c.getCategory())).append(',');
                    sb.append(csvField(e.getFramework() != null ? e.getFramework().name() : "")).append(',');
                    sb.append(csvField(e.getName())).append(',');
                    sb.append(csvField(e.getDoc())).append(',');
                    sb.append(csvField(e.getScm())).append(',');
                    sb.append(csvField(e.getDescription())).append(',');
                    sb.append(csvField(e.getType() != null ? e.getType().name() : "")).append(',');
                    sb.append(csvField(e.getSince()));
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
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

        sb.append("## Capabilities\n\n");
        sb.append("| Category | Framework | Name | Description | Type | Since |\n");
        sb.append("|----------|-----------|------|-------------|------|-------|\n");
        for (Capability c : capabilities) {
            if (c.getEntries().isEmpty()) {
                sb.append("| ").append(c.getCategory());
                sb.append(" | | | | | |\n");
            } else {
                for (FrameworkEntry e : c.getEntries()) {
                    sb.append("| ").append(c.getCategory());
                    sb.append(" | ").append(e.getFramework() != null ? e.getFramework() : "");
                    sb.append(" | ").append(mdEntryLink(e));
                    sb.append(" | ").append(e.getDescription() != null ? e.getDescription() : "");
                    sb.append(" | ").append(e.getType() != null ? e.getType() : "");
                    sb.append(" | ").append(e.getSince() != null ? e.getSince() : "");
                    sb.append(" |\n");
                }
            }
        }
        return sb.toString();
    }

    private String mdEntryLink(FrameworkEntry e) {
        if (e == null || e.getName() == null) return "---";
        var s = new StringBuilder();
        if (e.getDoc() != null) {
            s.append("[").append(e.getName()).append("](").append(e.getDoc()).append(")");
        } else {
            s.append(e.getName());
        }
        if (e.getScm() != null) {
            s.append(" ([SCM](").append(e.getScm()).append("))");
        }
        return s.toString();
    }

    private String csvField(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}