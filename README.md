# Spring vs Quarkus

A Quarkus web application that maintains a structured comparison of Spring Boot or equivalent Quarkus project/extension.

Data is stored in `data/comparisons.yaml` and loaded into an H2 in-memory database on startup. The web UI lets you browse, filter, add, and edit comparisons, then export or persist changes back to YAML.

## Prerequisites

- Java 21+
- Maven 3.9+

## Running the application

### Dev mode (live reload)

```bash
mvn quarkus:dev
```

Open [http://localhost:8080](http://localhost:8080) in your browser.

### Production build

```bash
mvn package
java -jar target/quarkus-app/quarkus-run.jar
```

## Data format

All comparison data lives in [`data/comparisons.yaml`](data/comparisons.yaml). Each entry represents a feature category with entries for one or both frameworks:

```yaml
- category: Spring Kafka
  description: null
  tags: "kafka, messaging"
  entries:
  - framework: Spring
    name: Spring Kafka
    url: https://spring.io/projects/spring-kafka
    github: https://github.com/spring-projects/spring-kafka
    description: Applies core Spring concepts to Kafka-based messaging
    type: SUB_PROJECT
    since: null
  - framework: Quarkus
    name: Quarkus Kafka
    url: https://quarkus.io/guides/kafka
    github: https://github.com/quarkusio/quarkus/tree/main/extensions/kafka-client
    description: Interact with Apache Kafka using Quarkus Messaging
    type: SUB_PROJECT
    since: null
  - framework: Spring
    name: spring-boot-starter-kafka
    url: https://spring.io/projects/spring-boot
    type: STARTER
  - framework: Quarkus
    name: quarkus-smallrye-reactive-messaging-kafka
    url: https://quarkus.io/extensions/io.quarkus/quarkus-smallrye-reactive-messaging-kafka
    type: EXTENSION
```

### Field reference

| Field | Description |
|-------|-------------|
| `category` | Feature area name (e.g. "Spring Security") |
| `description` | Optional description of the category |
| `tags` | Optional comma-separated tags for filtering |
| `entries[].framework` | `Spring` or `Quarkus` |
| `entries[].name` | Display name of the project/extension |
| `entries[].url` | Documentation URL |
| `entries[].github` | GitHub repository URL |
| `entries[].description` | Optional entry-level description |
| `entries[].type` | `SUB_PROJECT`, `STARTER`, or `EXTENSION` |
| `entries[].since` | Optional version when introduced |

### Updating the data

Edit `data/comparisons.yaml` directly to add, modify, or remove comparisons. Changes take effect on the next application restart.

You can also edit data through the web UI at `/comparisons` and then save back to YAML (see below).

## Exporting data

### CSV export

Download via the browser or curl:

```bash
curl -o spring-quarkus-comparison.csv http://localhost:8080/export/csv
```

The CSV uses `=HYPERLINK()` formulas so that names are clickable when opened in a spreadsheet application.

### Markdown export

```bash
curl -o spring-quarkus-comparison.md http://localhost:8080/export/markdown
```

Produces a full comparison table with summary statistics and per-category detail sections.

### Export via the UI

The web interface includes **Export CSV** and **Export Markdown** buttons in the toolbar above the comparison table.

## Saving to the local store

The application loads `data/comparisons.yaml` into an in-memory H2 database at startup. After making changes through the web UI, click the **Save to YAML** button (or POST to `/save`) to persist the current database state back to `data/comparisons.yaml`:

```bash
curl -X POST http://localhost:8080/save
```

This overwrites the YAML file with the current state of all comparisons.

## Creating a PR when comparisons.yaml changes

After modifying the data (either by editing the YAML directly or saving from the UI), create a pull request to track the change:

```bash
# Create a feature branch
git checkout -b update-comparisons

# Stage the updated data
git add data/comparisons.yaml

# Commit with a descriptive message
git commit -m "Update Spring vs Quarkus comparisons"

# Push and create the PR
git push -u origin update-comparisons
gh pr create --title "Update comparisons data" --body "Updated Spring vs Quarkus feature comparisons"
```

If you also want to include refreshed export files in the PR:

```bash
# Start the app, regenerate exports, then stop it
curl -o export/spring-quarkus-comparison.csv http://localhost:8080/export/csv
curl -o export/spring-quarkus-comparison.md http://localhost:8080/export/markdown

git add data/comparisons.yaml export/
git commit -m "Update comparisons and regenerate exports"
git push -u origin update-comparisons
gh pr create --title "Update comparisons data" --body "Updated comparisons YAML and regenerated CSV/Markdown exports"
```

## Project structure

```
data/
  comparisons.yaml              # Source of truth for all comparison data
export/                         # Generated export files (git-ignored)
src/main/java/.../
  model/
    Comparison.java             # JPA entity: feature category with entries
    FrameworkEntry.java         # JPA entity: individual framework entry
    Framework.java              # Enum: Spring, Quarkus
    FeatureType.java            # Enum: SUB_PROJECT, STARTER, EXTENSION
  repository/
    ComparisonRepository.java   # Panache repository with filtering queries
  resource/
    ComparisonResource.java     # JAX-RS endpoints (UI + export + save)
  service/
    DataService.java            # YAML/CSV load and save logic
    ExportService.java          # CSV and Markdown generation
src/main/resources/
  templates/                    # Qute HTML templates (list + form)
  application.properties        # Quarkus configuration
```