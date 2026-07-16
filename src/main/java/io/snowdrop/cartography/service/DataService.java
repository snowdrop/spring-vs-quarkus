package io.snowdrop.cartography.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opencsv.CSVReader;
import io.quarkus.runtime.StartupEvent;
import io.snowdrop.cartography.model.Capability;
import io.snowdrop.cartography.model.ComponentType;
import io.snowdrop.cartography.model.Framework;
import io.snowdrop.cartography.model.FrameworkEntry;
import io.snowdrop.cartography.repository.CapabilityRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DataService {

    private static final Logger LOG = Logger.getLogger(DataService.class);

    private static final Path YAML_FILE = Path.of("data/registry.yaml");
    private static final Path CSV_FILE = Path.of("spring-quarkus-comparison.csv");
    private static final Pattern HYPERLINK_PATTERN =
            Pattern.compile("=HYPERLINK\\(\"([^\"]+)\",\"([^\"]+)\"\\)");

    @Inject
    CapabilityRepository repository;

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        if (Files.exists(YAML_FILE)) {
            loadFromYaml();
        } else if (Files.exists(CSV_FILE)) {
            importFromCsv();
            saveToYaml();
        } else {
            LOG.warn("No data/capabilities.yaml or spring-quarkus-comparison.csv found");
        }
    }

    void loadFromYaml() {
        try {
            var mapper = createYamlMapper();
            List<CapabilityDto> dtos = mapper.readValue(
                    YAML_FILE.toFile(), new TypeReference<List<CapabilityDto>>() {});
            int withEntries = 0;
            int withoutEntries = 0;
            for (CapabilityDto dto : dtos) {
                Capability c = dto.toEntity();
                repository.persist(c);
                if (c.getEntries().isEmpty()) {
                    withoutEntries++;
                    LOG.warnf("Capability '%s' has no framework entries (standalone capability with no registry data)", c.getCategory());
                } else {
                    withEntries++;
                }
            }
            LOG.infof("Loaded %d capabilities from %s (%d with entries, %d without entries)",
                    dtos.size(), YAML_FILE, withEntries, withoutEntries);
        } catch (IOException e) {
            LOG.error("Failed to load YAML", e);
        }
    }

    public void saveToYaml() {
        try {
            Files.createDirectories(YAML_FILE.getParent());
            var mapper = createYamlMapper();
            List<Capability> capabilities = repository.findAllOrdered();
            List<CapabilityDto> dtos = capabilities.stream().map(CapabilityDto::fromEntity).toList();
            String yaml = mapper.writeValueAsString(dtos);
            yaml = yaml.replaceAll("(?m)^- category:", "\n- category:").stripLeading();
            Files.writeString(YAML_FILE, yaml);
            LOG.infof("Saved %d capabilities to %s", dtos.size(), YAML_FILE);
        } catch (IOException e) {
            LOG.error("Failed to save YAML", e);
        }
    }

    void importFromCsv() {
        try (var reader = new CSVReader(new FileReader(CSV_FILE.toFile()))) {
            reader.readNext();
            String[] row;
            Capability current = null;

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
                        current = new Capability(col0.label());
                        addProjectEntry(current, Framework.Spring, col0, col1);
                        if (col3 != null) {
                            addProjectEntry(current, Framework.Quarkus, col3, col4);
                        }
                    } else {
                        current = new Capability(col3.label());
                        addProjectEntry(current, Framework.Quarkus, col3, col4);
                    }
                }

                if (current == null) continue;

                addEntriesFromRow(current, col2, col5, col6, col7);
            }

            if (current != null) {
                repository.persist(current);
            }

            LOG.infof("Imported %d capabilities from CSV", repository.count());
        } catch (Exception e) {
            LOG.error("Failed to import CSV", e);
        }
    }

    private void addProjectEntry(Capability c, Framework framework, HyperlinkValue project, HyperlinkValue github) {
        var entry = new FrameworkEntry();
        entry.setFramework(framework);
        entry.setName(project.label());
        entry.setDoc(project.url());
        if (github != null) {
            entry.setScm(github.url());
        }
        entry.setType(framework == Framework.Spring ? ComponentType.STARTER : ComponentType.EXTENSION);
        c.addEntry(entry);
    }

    private void addEntriesFromRow(Capability capability,
                                    HyperlinkValue springSub, HyperlinkValue quarkusSub,
                                    HyperlinkValue starter, HyperlinkValue extension) {
        if (springSub != null) {
            var e = new FrameworkEntry();
            e.setFramework(Framework.Spring);
            e.setName(springSub.label());
            e.setDoc(springSub.url());
            e.setType(ComponentType.STARTER);
            capability.addEntry(e);
        }
        if (quarkusSub != null) {
            var e = new FrameworkEntry();
            e.setFramework(Framework.Quarkus);
            e.setName(quarkusSub.label());
            e.setDoc(quarkusSub.url());
            e.setType(ComponentType.EXTENSION);
            capability.addEntry(e);
        }
        if (starter != null) {
            var e = new FrameworkEntry();
            e.setFramework(Framework.Spring);
            e.setName(starter.label());
            e.setDoc(starter.url());
            e.setType(ComponentType.STARTER);
            capability.addEntry(e);
        }
        if (extension != null) {
            var e = new FrameworkEntry();
            e.setFramework(Framework.Quarkus);
            e.setName(extension.label());
            e.setDoc(extension.url());
            e.setType(ComponentType.EXTENSION);
            capability.addEntry(e);
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
        var mapper = new ObjectMapper(factory);
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        return mapper;
    }

    record HyperlinkValue(String url, String label) {}

    public record CapabilityDto(
            String category,
            String description,
            String tags,
            String topic,
            String reviewBy,
            LocalDate reviewDate,
            String quarkusStatus,
            List<FrameworkEntryDto> entries
    ) {
        static CapabilityDto fromEntity(Capability c) {
            return new CapabilityDto(
                    c.getCategory(),
                    c.getDescription(),
                    c.getTags(),
                    c.getTopic(),
                    c.getReviewBy(),
                    c.getReviewDate(),
                    c.getQuarkusStatus(),
                    c.getEntries().stream().map(FrameworkEntryDto::fromEntity).toList()
            );
        }

        Capability toEntity() {
            var c = new Capability(category);
            c.setDescription(description);
            c.setTags(tags);
            c.setTopic(topic);
            c.setReviewBy(reviewBy);
            c.setReviewDate(reviewDate);
            c.setQuarkusStatus(quarkusStatus);
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
            String doc,
            String scm,
            String description,
            ComponentType type,
            String since,
            String comment
    ) {
        static FrameworkEntryDto fromEntity(FrameworkEntry e) {
            return new FrameworkEntryDto(
                    e.getFramework(),
                    e.getName(),
                    e.getDoc(),
                    e.getScm(),
                    e.getDescription(),
                    e.getType(),
                    e.getSince(),
                    e.getComment()
            );
        }

        FrameworkEntry toEntity() {
            var e = new FrameworkEntry();
            e.setFramework(framework);
            e.setName(name);
            e.setDoc(doc);
            e.setScm(scm);
            e.setDescription(description);
            e.setType(type);
            e.setSince(since);
            e.setComment(comment);
            return e;
        }
    }
}
