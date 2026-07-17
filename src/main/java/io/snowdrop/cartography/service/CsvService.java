package io.snowdrop.cartography.service;

import com.opencsv.CSVReader;
import io.snowdrop.cartography.model.Capability;
import io.snowdrop.cartography.model.ComponentType;
import io.snowdrop.cartography.model.Framework;
import io.snowdrop.cartography.model.FrameworkEntry;
import io.snowdrop.cartography.repository.CapabilityRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CsvService {

    private static final Logger LOG = Logger.getLogger(CsvService.class);

    @Inject
    CapabilityRepository repository;

    public String exportCsv() {
        var sb = new StringBuilder();
        sb.append("Capability,Description,Tags,Framework,Name,Doc,SCM,Type,Since,Review By,Review Date,Quarkus Status,Status Comment\n");

        List<Capability> capabilities = repository.findAllOrdered();
        for (Capability c : capabilities) {
            if (c.getEntries().isEmpty()) {
                sb.append(csvField(c.getCategory())).append(',');
                sb.append(csvField(c.getDescription())).append(',');
                sb.append(csvField(c.getTags()));
                sb.append(",,,,,,");
                sb.append(',').append(csvField(c.getReviewBy()));
                sb.append(',').append(csvField(c.getReviewDate() != null ? c.getReviewDate().toString() : null));
                sb.append(',').append(csvField(c.getQuarkusStatus()));
                sb.append(',').append(csvField(c.getStatusComment()));
                sb.append('\n');
            } else {
                boolean first = true;
                var sorted = c.getEntries().stream()
                        .sorted(Comparator.comparingInt(e -> e.getFramework() != null ? e.getFramework().ordinal() : Integer.MAX_VALUE))
                        .toList();
                for (FrameworkEntry e : sorted) {
                    sb.append(csvField(c.getCategory())).append(',');
                    sb.append(csvField(c.getDescription())).append(',');
                    sb.append(csvField(c.getTags())).append(',');
                    sb.append(csvField(e.getFramework() != null ? e.getFramework().name() : "")).append(',');
                    sb.append(csvField(e.getName())).append(',');
                    sb.append(csvField(e.getDoc())).append(',');
                    sb.append(csvField(e.getScm())).append(',');
                    sb.append(csvField(e.getType() != null ? e.getType().name() : "")).append(',');
                    sb.append(csvField(e.getSince()));
                    if (first) {
                        sb.append(',').append(csvField(c.getReviewBy()));
                        sb.append(',').append(csvField(c.getReviewDate() != null ? c.getReviewDate().toString() : null));
                        sb.append(',').append(csvField(c.getQuarkusStatus()));
                        sb.append(',').append(csvField(c.getStatusComment()));
                        first = false;
                    } else {
                        sb.append(",,,,");
                    }
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }

    public String exportGSheetCsv() {
        var sb = new StringBuilder();
        sb.append("Capability,Description,Tags,Framework,Name,Doc,SCM,Type,Since,Review By,Review Date,Quarkus Status,Status Comment\n");

        List<Capability> capabilities = repository.findAllOrdered();
        for (Capability c : capabilities) {
            if (c.getEntries().isEmpty()) {
                sb.append(csvField(c.getCategory())).append(',');
                sb.append(csvField(c.getDescription())).append(',');
                sb.append(csvField(c.getTags()));
                sb.append(",,,,,,");
                sb.append(',').append(csvField(c.getReviewBy()));
                sb.append(',').append(csvField(c.getReviewDate() != null ? c.getReviewDate().toString() : null));
                sb.append(',').append(csvField(c.getQuarkusStatus()));
                sb.append(',').append(csvField(c.getStatusComment()));
                sb.append('\n');
            } else {
                boolean first = true;
                var sorted = c.getEntries().stream()
                        .sorted(Comparator.comparingInt(e -> e.getFramework() != null ? e.getFramework().ordinal() : Integer.MAX_VALUE))
                        .toList();
                for (FrameworkEntry e : sorted) {
                    sb.append(csvField(c.getCategory())).append(',');
                    sb.append(csvField(c.getDescription())).append(',');
                    sb.append(csvField(c.getTags())).append(',');
                    sb.append(csvField(e.getFramework() != null ? e.getFramework().name() : "")).append(',');
                    sb.append(csvField(e.getName())).append(',');
                    sb.append(hyperlinkField(e.getDoc(), e.getName())).append(',');
                    sb.append(hyperlinkField(e.getScm(), e.getScmRepoName())).append(',');
                    sb.append(csvField(e.getType() != null ? e.getType().name() : "")).append(',');
                    sb.append(csvField(e.getSince()));
                    if (first) {
                        sb.append(',').append(csvField(c.getReviewBy()));
                        sb.append(',').append(csvField(c.getReviewDate() != null ? c.getReviewDate().toString() : null));
                        sb.append(',').append(csvField(c.getQuarkusStatus()));
                        sb.append(',').append(csvField(c.getStatusComment()));
                        first = false;
                    } else {
                        sb.append(",,,,");
                    }
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }

    private String hyperlinkField(String url, String label) {
        if (url == null || url.isBlank()) return "";
        String safeLabel = (label != null && !label.isBlank()) ? label : url;
        return "\"=HYPERLINK(\"\"" + url.replace("\"", "\"\"") + "\"\",\"\"" + safeLabel.replace("\"", "\"\"") + "\"\")\"";
    }

    private String csvField(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
