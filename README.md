# Inventory and comparison of capabilities across frameworks

The goal of this project is to inventory the capabilities (REST, Web, JPA, persistence, transactions, security, etc.) offered by frameworks like Quarkus and Spring Boot, and to compare which framework supports each capability as a starter or extension. Each capability references the frameworks that support it, along with a description, a link to the project home page, and the source repository.

Data is stored in `data/capabilities.yaml` and loaded into an H2 in-memory database on startup. The web UI lets you browse, filter, add, and edit capabilities, then export or persist changes back to YAML.

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

All capability data lives in [`data/capabilities.yaml`](data/capabilities.yaml). Each capability represents a domain area (e.g. security, web, messaging) with entries for one or both frameworks:

```yaml
- category: streaming
  description: Technology supporting high-scalable distributed event streaming.
  tags: "kafka, messaging, event"
  topic: Messaging
  reviewBy: Charles Moulliard
  reviewDate: 2026-07-15
  entries:
    - framework: Spring
      name: spring-boot-starter-kafka
      url: https://spring.io/projects/spring-kafka
      github: https://github.com/spring-projects/spring-kafka
      description: Starter for using Apache Kafka
      type: STARTER
      since: Nov 2025
    - framework: Quarkus
      name: quarkus-kafka-client
      url: https://quarkus.io/guides/kafka
      github: https://github.com/quarkusio/quarkus/tree/main/extensions/kafka-client
      description: Connect to Apache Kafka with its native API
      type: EXTENSION
      since: "Mar 6, 2019"
    - framework: Quarkus
      name: quarkus-kafka-streams
      url: https://quarkus.io/guides/kafka
      github: https://github.com/quarkusio/quarkus/tree/main/extensions/kafka-streams
      description: Implement stream processing applications based on Apache Kafka
      type: EXTENSION
      since: "June 19, 2019"
```

### Field reference

| Field | Description                                                               |
|-------|---------------------------------------------------------------------------|
| `category` | Capability name (e.g. security, messaging, streaming, web, rest)          |
| `description` | Optional description of the capability                                    |
| `tags` | Optional comma-separated tags for filtering                               |
| `topic` | Broader domain this capability belongs to (e.g. "Security", "Data", "Web") |
| `reviewBy` | Optional reviewer name                                                    |
| `reviewDate` | Optional review date (ISO format)                                         |
| `entries[].framework` | `Spring` or `Quarkus`                                                     |
| `entries[].name` | Display name of the starter or extension                                  |
| `entries[].url` | Project home page or documentation URL                                    |
| `entries[].github` | GitHub repository URL                                                     |
| `entries[].description` | Optional entry-level description                                          |
| `entries[].type` | `STARTER` (Spring) or `EXTENSION` (Quarkus)                               |
| `entries[].since` | Optional version or year when introduced                                  |

### Updating the data

Edit `data/capabilities.yaml` directly to add, modify, or remove capabilities. Changes take effect on the next application restart.

You can also edit data through the web UI at `/capabilities` and then save back to YAML (see below).

## Fetching Spring starters and Quarkus extensions

A standalone [JBang](https://www.jbang.dev/) script is included to fetch the full list of Spring Boot starters from [start.spring.io](https://start.spring.io) and Quarkus extensions from the [Quarkus registry](https://registry.quarkus.io). It requires no build step — JBang resolves dependencies and runs the script directly.

### Prerequisites

Install JBang (if not already available):

```bash
curl -Ls https://sh.jbang.dev | bash -s - app setup
```

### Usage

```bash
jbang src/main/java/io/snowdrop/cartography/cli/FetchComponents.java [options]
```

### Options

| Option | Description |
|--------|-------------|
| `-t`, `--target` | Framework(s) to fetch: `spring`, `quarkus`, or `both` (default: `both`) |
| `-f`, `--format` | Output format: `md` (Markdown table) or `csv` (default: `md`) |
| `-o <file>` | Write output to a file instead of stdout |
| `--boot-version` | Spring Boot version to query (default: latest stable) |
| `-h`, `--help` | Show help |

### Examples

Fetch both Spring starters and Quarkus extensions as a Markdown table:

```bash
jbang src/main/java/io/snowdrop/cartography/cli/FetchComponents.java
```

Fetch only Spring starters and save as CSV:

```bash
jbang src/main/java/io/snowdrop/cartography/cli/FetchComponents.java -t spring -f csv -o spring-starters.csv
```

Fetch only Quarkus extensions:

```bash
jbang src/main/java/io/snowdrop/cartography/cli/FetchComponents.java -t quarkus
```

Fetch Spring starters for a specific Boot version:

```bash
jbang src/main/java/io/snowdrop/cartography/cli/FetchComponents.java -t spring --boot-version 3.4.1
```

When using `--target both`, the script maps Spring starter categories to Quarkus extension categories and attempts to match equivalent components side by side.

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

Produces a full comparison table with summary statistics and per-capability detail sections.

### Export via the UI

The web interface includes **Export CSV** and **Export Markdown** buttons in the toolbar above the capabilities table.

## Saving to the local store

The application loads `data/capabilities.yaml` into an in-memory H2 database at startup. After making changes through the web UI, click the **Save to YAML** button (or POST to `/save`) to persist the current database state back to `data/capabilities.yaml`:

```bash
curl -X POST http://localhost:8080/save
```

This overwrites the YAML file with the current state of all capabilities.

## Creating a PR when capabilities.yaml changes

After modifying the data (either by editing the YAML directly or saving from the UI), create a pull request to track the change:

```bash
git checkout -b update-capabilities
git add data/capabilities.yaml
git commit -m "Add new capability: authentication for Spring and Quarkus."
git push -u origin update-capabilities
gh pr create --title "Update capabilities data" --body "Add new capability: authentication for Spring and Quarkus."
```

If you also want to include refreshed export files in the PR:

```bash
curl -o export/spring-quarkus-capabilities.csv http://localhost:8080/export/csv
curl -o export/spring-quarkus-capabilities.md http://localhost:8080/export/markdown

git add data/capabilities.yaml export/
git commit -m "Update capabilities and regenerate exports"
git push -u origin update-capabilities
gh pr create --title "Update capabilities data" --body "Updated capabilities YAML and regenerated CSV/Markdown exports"
```

## Project structure

```
data/
  capabilities.yaml              # Source of truth for all capability data
export/                          # Generated export files (git-ignored)
src/main/java/.../
  model/
    Capability.java              # JPA entity: capability with framework entries
    FrameworkEntry.java          # JPA entity: individual framework entry
    Framework.java               # Enum: Spring, Quarkus
    ComponentType.java           # Enum: STARTER, EXTENSION
  repository/
    CapabilityRepository.java    # Panache repository with filtering queries
  resource/
    CapabilityResource.java      # JAX-RS endpoints (UI + export + save)
  service/
    DataService.java             # YAML/CSV load and save logic
    ExportService.java           # CSV and Markdown generation
src/main/resources/
  templates/                     # Qute HTML templates (list + form)
  application.properties         # Quarkus configuration
```