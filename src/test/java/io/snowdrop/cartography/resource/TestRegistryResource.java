package io.snowdrop.cartography.resource;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class TestRegistryResource implements QuarkusTestResourceLifecycleManager {

    private static final Path FIXTURE = Path.of("src/test/resources/test-registry.yaml");
    private Path tempFile;

    @Override
    public Map<String, String> start() {
        try {
            tempFile = Files.createTempFile("test-registry-", ".yaml");
            Files.copy(FIXTURE, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return Map.of("registry.yaml.path", tempFile.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void stop() {
        if (tempFile != null) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
            }
        }
    }
}
