package io.snowdrop.cartography.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.jboss.logging.Logger;

@ApplicationScoped
public class YamlDataService {

    private static final Logger LOG = Logger.getLogger(YamlDataService.class);

    private static final Path YAML_FILE = Path.of("data/registry.yaml");
    private static final Path CSV_FILE = Path.of("spring-quarkus-comparison.csv");

    @Inject
    CapabilityRepository repository;

    @Inject
    CsvService csvService;

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        if (repository.count() > 0) {
            LOG.infof("Database already contains %d capabilities, skipping YAML import", repository.count());
            return;
        }
        if (Files.exists(YAML_FILE)) {
            loadFromYaml();
        } else {
            LOG.warn("No data/registry.yaml found");
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

    public record CapabilityDto(
            String category,
            String description,
            String tags,
            String topic,
            String reviewBy,
            LocalDate reviewDate,
            String quarkusStatus,
            String statusComment,
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
                    c.getStatusComment(),
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
            c.setStatusComment(statusComment);
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
