package io.snowdrop.springvsquarkus.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.opencsv.CSVReader;
import io.quarkus.runtime.StartupEvent;
import io.snowdrop.springvsquarkus.model.Comparison;
import io.snowdrop.springvsquarkus.model.FeatureType;
import io.snowdrop.springvsquarkus.model.Framework;
import io.snowdrop.springvsquarkus.model.FrameworkEntry;
import io.snowdrop.springvsquarkus.repository.ComparisonRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DataService {

    private static final Logger LOG = Logger.getLogger(DataService.class);

    private static final Path YAML_FILE = Path.of("data/comparisons.yaml");
    private static final Path CSV_FILE = Path.of("spring-quarkus-comparison.csv");
    private static final Pattern HYPERLINK_PATTERN =
            Pattern.compile("=HYPERLINK\\(\"([^\"]+)\",\"([^\"]+)\"\\)");

    @Inject
    ComparisonRepository repository;

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        if (Files.exists(YAML_FILE)) {
            loadFromYaml();
        } else if (Files.exists(CSV_FILE)) {
            importFromCsv();
            saveToYaml();
        } else {
            LOG.warn("No data/comparisons.yaml or spring-quarkus-comparison.csv found");
        }
    }

    void loadFromYaml() {
        try {
            var mapper = createYamlMapper();
            List<ComparisonDto> dtos = mapper.readValue(
                    YAML_FILE.toFile(), new TypeReference<List<ComparisonDto>>() {});
            for (ComparisonDto dto : dtos) {
                Comparison c = dto.toEntity();
                repository.persist(c);
            }
            LOG.infof("Loaded %d comparisons from %s", dtos.size(), YAML_FILE);
        } catch (IOException e) {
            LOG.error("Failed to load YAML", e);
        }
    }

    public void saveToYaml() {
        try {
            Files.createDirectories(YAML_FILE.getParent());
            var mapper = createYamlMapper();
            List<Comparison> comparisons = repository.findAllOrdered();
            List<ComparisonDto> dtos = comparisons.stream().map(ComparisonDto::fromEntity).toList();
            mapper.writeValue(YAML_FILE.toFile(), dtos);
            LOG.infof("Saved %d comparisons to %s", dtos.size(), YAML_FILE);
        } catch (IOException e) {
            LOG.error("Failed to save YAML", e);
        }
    }

    void importFromCsv() {
        try (var reader = new CSVReader(new FileReader(CSV_FILE.toFile()))) {
            reader.readNext();
            String[] row;
            Comparison current = null;

            while ((row = reader.readNext()) != null) {
                if (row.length < 10) continue;

                var col0 = parseHyperlink(row[0]);
                var col1 = parseHyperlink(row[1]);
                var col2 = parseHyperlink(row[2]);
                var col3 = parseHyperlink(row[3]);
                var col4 = parseHyperlink(row[4]);
                var col5 = parseHyperlink(row[5]);
                var col6 = parseHyperlink(row[6]);
                var col7 = parseHyperlink(row[7]);

                boolean isNewProject = col0 != null;
                boolean isQuarkusOnly = col0 == null && col2 == null && col3 != null;

                if (isNewProject || isQuarkusOnly) {
                    if (current != null) {
                        repository.persist(current);
                    }

                    if (isNewProject) {
                        current = new Comparison(col0.label());
                        addProjectEntry(current, Framework.Spring, col0, col1);
                        if (col3 != null) {
                            addProjectEntry(current, Framework.Quarkus, col3, col4);
                        }
                    } else {
                        current = new Comparison(col3.label());
                        addProjectEntry(current, Framework.Quarkus, col3, col4);
                    }
                }

                if (current == null) continue;

                addEntriesFromRow(current, col2, col5, col6, col7);
            }

            if (current != null) {
                repository.persist(current);
            }

            LOG.infof("Imported %d comparisons from CSV", repository.count());
        } catch (Exception e) {
            LOG.error("Failed to import CSV", e);
        }
    }

    private void addProjectEntry(Comparison c, Framework framework, HyperlinkValue project, HyperlinkValue github) {
        var entry = new FrameworkEntry();
        entry.setFramework(framework);
        entry.setName(project.label());
        entry.setUrl(project.url());
        if (github != null) {
            entry.setGithub(github.url());
        }
        entry.setType(FeatureType.SUB_PROJECT);
        c.addEntry(entry);
    }

    private void addEntriesFromRow(Comparison comparison,
                                    HyperlinkValue springSub, HyperlinkValue quarkusSub,
                                    HyperlinkValue starter, HyperlinkValue extension) {
        if (springSub != null) {
            var e = new FrameworkEntry();
            e.setFramework(Framework.Spring);
            e.setName(springSub.label());
            e.setUrl(springSub.url());
            e.setType(FeatureType.SUB_PROJECT);
            comparison.addEntry(e);
        }
        if (quarkusSub != null) {
            var e = new FrameworkEntry();
            e.setFramework(Framework.Quarkus);
            e.setName(quarkusSub.label());
            e.setUrl(quarkusSub.url());
            e.setType(FeatureType.SUB_PROJECT);
            comparison.addEntry(e);
        }
        if (starter != null) {
            var e = new FrameworkEntry();
            e.setFramework(Framework.Spring);
            e.setName(starter.label());
            e.setUrl(starter.url());
            e.setType(FeatureType.STARTER);
            comparison.addEntry(e);
        }
        if (extension != null) {
            var e = new FrameworkEntry();
            e.setFramework(Framework.Quarkus);
            e.setName(extension.label());
            e.setUrl(extension.url());
            e.setType(FeatureType.EXTENSION);
            comparison.addEntry(e);
        }
    }

    static HyperlinkValue parseHyperlink(String cellValue) {
        if (cellValue == null || cellValue.isBlank()) return null;
        Matcher m = HYPERLINK_PATTERN.matcher(cellValue.trim());
        if (m.matches()) {
            return new HyperlinkValue(m.group(1), m.group(2));
        }
        String trimmed = cellValue.trim();
        if (!trimmed.isEmpty()) {
            return new HyperlinkValue(null, trimmed);
        }
        return null;
    }

    private ObjectMapper createYamlMapper() {
        var factory = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        return new ObjectMapper(factory);
    }

    record HyperlinkValue(String url, String label) {}

    public record ComparisonDto(
            String category,
            String description,
            String tags,
            List<FrameworkEntryDto> entries
    ) {
        static ComparisonDto fromEntity(Comparison c) {
            return new ComparisonDto(
                    c.getCategory(),
                    c.getDescription(),
                    c.getTags(),
                    c.getEntries().stream().map(FrameworkEntryDto::fromEntity).toList()
            );
        }

        Comparison toEntity() {
            var c = new Comparison(category);
            c.setDescription(description);
            c.setTags(tags);
            if (entries != null) {
                for (var dto : entries) {
                    c.addEntry(dto.toEntity());
                }
            }
            return c;
        }
    }

    public record FrameworkEntryDto(
            Framework framework,
            String name,
            String url,
            String github,
            String description,
            FeatureType type,
            String since
    ) {
        static FrameworkEntryDto fromEntity(FrameworkEntry e) {
            return new FrameworkEntryDto(
                    e.getFramework(),
                    e.getName(),
                    e.getUrl(),
                    e.getGithub(),
                    e.getDescription(),
                    e.getType(),
                    e.getSince()
            );
        }

        FrameworkEntry toEntity() {
            var e = new FrameworkEntry();
            e.setFramework(framework);
            e.setName(name);
            e.setUrl(url);
            e.setGithub(github);
            e.setDescription(description);
            e.setType(type);
            e.setSince(since);
            return e;
        }
    }
}
