package io.snowdrop.cartography.store;

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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import org.jboss.logging.Logger;

@ApplicationScoped
public class RegistryStore {

    private static final Logger LOG = Logger.getLogger(RegistryStore.class);

    @ConfigProperty(name = "registry.yaml.path", defaultValue = "data/registry.yaml")
    String yamlPath;

    private final CopyOnWriteArrayList<Capability> capabilities = new CopyOnWriteArrayList<>();
    private final AtomicLong nextCapabilityId = new AtomicLong(1);
    private final AtomicLong nextEntryId = new AtomicLong(1);

    private Path yamlFile() {
        return Path.of(yamlPath);
    }

    void onStart(@Observes StartupEvent ev) {
        if (Files.exists(yamlFile())) {
            loadFromYaml();
        } else {
            LOG.warnf("No YAML file found at %s", yamlPath);
        }
    }

    public List<Capability> findAllOrdered() {
        return capabilities.stream()
                .sorted(Comparator.comparing(Capability::getCategory, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Capability findById(Long id) {
        return capabilities.stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public List<Capability> findFiltered(String nameFilter, Boolean springSupported, Boolean quarkusSupported) {
        Predicate<Capability> predicate = c -> true;

        if (nameFilter != null && !nameFilter.isBlank()) {
            String lower = nameFilter.toLowerCase();
            predicate = predicate.and(c -> c.getCategory().toLowerCase().contains(lower));
        }
        if (springSupported != null) {
            predicate = springSupported
                    ? predicate.and(Capability::isSpringSupported)
                    : predicate.and(c -> !c.isSpringSupported());
        }
        if (quarkusSupported != null) {
            predicate = quarkusSupported
                    ? predicate.and(Capability::isQuarkusSupported)
                    : predicate.and(c -> !c.isQuarkusSupported());
        }

        return capabilities.stream()
                .filter(predicate)
                .sorted(Comparator.comparing(Capability::getCategory, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public long count() {
        return capabilities.size();
    }

    public long countBoth() {
        return capabilities.stream()
                .filter(c -> c.isSpringSupported() && c.isQuarkusSupported())
                .count();
    }

    public long countSpringOnly() {
        return capabilities.stream()
                .filter(c -> c.isSpringSupported() && !c.isQuarkusSupported())
                .count();
    }

    public long countQuarkusOnly() {
        return capabilities.stream()
                .filter(c -> !c.isSpringSupported() && c.isQuarkusSupported())
                .count();
    }

    public void persist(Capability c) {
        c.setId(nextCapabilityId.getAndIncrement());
        assignEntryIds(c);
        capabilities.add(c);
        saveToYaml();
    }

    public void update(Capability c) {
        assignEntryIds(c);
        capabilities.replaceAll(existing -> existing.getId().equals(c.getId()) ? c : existing);
        saveToYaml();
    }

    public void deleteById(Long id) {
        capabilities.removeIf(c -> c.getId().equals(id));
        saveToYaml();
    }

    public void moveEntry(Long entryId, Long fromCapabilityId, Long toCapabilityId) {
        Capability from = findById(fromCapabilityId);
        Capability to = findById(toCapabilityId);
        if (from == null || to == null) return;

        FrameworkEntry entry = from.getEntries().stream()
                .filter(e -> e.getId().equals(entryId))
                .findFirst()
                .orElse(null);
        if (entry == null) return;

        from.removeEntry(entry);
        to.addEntry(entry);
        saveToYaml();
    }

    public void saveToYaml() {
        try {
            Path parent = yamlFile().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            var mapper = createYamlMapper();
            List<Capability> ordered = findAllOrdered();
            List<CapabilityDto> dtos = ordered.stream().map(CapabilityDto::fromEntity).toList();
            String yaml = mapper.writeValueAsString(dtos);
            yaml = yaml.replaceAll("(?m)^- category:", "\n- category:").stripLeading();
            Files.writeString(yamlFile(), yaml);
            LOG.infof("Saved %d capabilities to %s", dtos.size(), yamlFile());
        } catch (IOException e) {
            LOG.error("Failed to save YAML", e);
        }
    }

    private void loadFromYaml() {
        try {
            var mapper = createYamlMapper();
            List<CapabilityDto> dtos = mapper.readValue(
                    yamlFile().toFile(), new TypeReference<List<CapabilityDto>>() {});

            Map<String, Capability> byCategory = new LinkedHashMap<>();
            for (CapabilityDto dto : dtos) {
                Capability c = dto.toEntity();
                Capability existing = byCategory.get(c.getCategory());
                if (existing != null) {
                    var existingNames = existing.getEntries().stream()
                            .map(e -> e.getFramework() + ":" + e.getName())
                            .collect(java.util.stream.Collectors.toSet());
                    for (FrameworkEntry e : c.getEntries()) {
                        String key = e.getFramework() + ":" + e.getName();
                        if (!existingNames.contains(key)) {
                            existing.addEntry(e);
                        }
                    }
                } else {
                    byCategory.put(c.getCategory(), c);
                }
            }

            capabilities.clear();
            for (Capability c : byCategory.values()) {
                c.setId(nextCapabilityId.getAndIncrement());
                assignEntryIds(c);
                capabilities.add(c);
            }
            int dupes = dtos.size() - byCategory.size();
            LOG.infof("Loaded %d capabilities from %s (%d duplicates merged)",
                    byCategory.size(), yamlFile(), dupes);
        } catch (IOException e) {
            LOG.error("Failed to load YAML", e);
        }
    }

    private void assignEntryIds(Capability c) {
        for (FrameworkEntry e : c.getEntries()) {
            if (e.getId() == null) {
                e.setId(nextEntryId.getAndIncrement());
            }
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
