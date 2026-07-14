package io.snowdrop.springvsquarkus.resource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.snowdrop.springvsquarkus.model.Comparison;
import io.snowdrop.springvsquarkus.model.FeatureType;
import io.snowdrop.springvsquarkus.model.Framework;
import io.snowdrop.springvsquarkus.model.FrameworkEntry;
import io.snowdrop.springvsquarkus.repository.ComparisonRepository;
import io.snowdrop.springvsquarkus.service.DataService;
import io.snowdrop.springvsquarkus.service.ExportService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;

@Path("/")
public class ComparisonResource {

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance list(
                List<Comparison> comparisons,
                long totalCount,
                long bothCount,
                long springOnlyCount,
                long quarkusOnlyCount,
                String nameFilter,
                String springFilter,
                String quarkusFilter,
                boolean saved);

        public static native TemplateInstance form(
                Comparison comparison,
                boolean isNew,
                List<FeatureType> featureTypes,
                List<Framework> frameworks);
    }

    @Inject
    ComparisonRepository repository;

    @Inject
    DataService dataService;

    @Inject
    ExportService exportService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response root() {
        return Response.seeOther(URI.create("/comparisons")).build();
    }

    @GET
    @Path("/comparisons")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance list(
            @QueryParam("name") String name,
            @QueryParam("spring") String spring,
            @QueryParam("quarkus") String quarkus,
            @QueryParam("saved") String saved) {

        Boolean springSupported = "yes".equals(spring) ? Boolean.TRUE
                : "no".equals(spring) ? Boolean.FALSE : null;
        Boolean quarkusSupported = "yes".equals(quarkus) ? Boolean.TRUE
                : "no".equals(quarkus) ? Boolean.FALSE : null;

        List<Comparison> comparisons;
        if ((name == null || name.isBlank()) && springSupported == null && quarkusSupported == null) {
            comparisons = repository.findAllOrdered();
        } else {
            comparisons = repository.findFiltered(name, springSupported, quarkusSupported);
        }

        return Templates.list(
                comparisons,
                repository.count(),
                repository.countBoth(),
                repository.countSpringOnly(),
                repository.countQuarkusOnly(),
                name != null ? name : "",
                spring != null ? spring : "",
                quarkus != null ? quarkus : "",
                "true".equals(saved));
    }

    @GET
    @Path("/comparisons/new")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance newForm() {
        return Templates.form(new Comparison(""), true,
                List.of(FeatureType.values()), List.of(Framework.values()));
    }

    @GET
    @Path("/comparisons/{id}/edit")
    @Produces(MediaType.TEXT_HTML)
    public Response editForm(@PathParam("id") Long id) {
        Comparison c = repository.findById(id);
        if (c == null) {
            return Response.seeOther(URI.create("/comparisons")).build();
        }
        return Response.ok(Templates.form(c, false,
                List.of(FeatureType.values()), List.of(Framework.values()))).build();
    }

    @POST
    @Path("/comparisons")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Transactional
    public Response create(
            @FormParam("category") String category,
            @FormParam("description") String description,
            @FormParam("tags") String tags,
            @FormParam("entriesJson") String entriesJson) {

        Comparison c = new Comparison(category);
        c.setDescription(blankToNull(description));
        c.setTags(blankToNull(tags));
        applyEntries(c, entriesJson);
        repository.persist(c);
        return Response.seeOther(URI.create("/comparisons/" + c.getId() + "/edit")).build();
    }

    @POST
    @Path("/comparisons/{id}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Transactional
    public Response update(
            @PathParam("id") Long id,
            @FormParam("category") String category,
            @FormParam("description") String description,
            @FormParam("tags") String tags,
            @FormParam("entriesJson") String entriesJson) {

        Comparison c = repository.findById(id);
        if (c == null) {
            return Response.seeOther(URI.create("/comparisons")).build();
        }

        c.setCategory(category);
        c.setDescription(blankToNull(description));
        c.setTags(blankToNull(tags));
        c.getEntries().clear();
        applyEntries(c, entriesJson);
        return Response.seeOther(URI.create("/comparisons/" + id + "/edit")).build();
    }

    @POST
    @Path("/comparisons/{id}/delete")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        repository.deleteById(id);
        return Response.seeOther(URI.create("/comparisons")).build();
    }

    @GET
    @Path("/export/csv")
    @Produces("text/csv")
    public Response exportCsv() {
        String csv = exportService.exportCsv();
        return Response.ok(csv)
                .header("Content-Disposition", "attachment; filename=\"spring-quarkus-comparison.csv\"")
                .build();
    }

    @GET
    @Path("/export/markdown")
    @Produces("text/markdown")
    public Response exportMarkdown() {
        String md = exportService.exportMarkdown();
        return Response.ok(md)
                .header("Content-Disposition", "attachment; filename=\"spring-quarkus-comparison.md\"")
                .build();
    }

    @POST
    @Path("/save")
    @Transactional
    public Response saveToYaml() {
        dataService.saveToYaml();
        return Response.seeOther(URI.create("/comparisons?saved=true")).build();
    }

    private void applyEntries(Comparison c, String entriesJson) {
        if (entriesJson == null || entriesJson.isBlank()) return;
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<EntryDto> entries = mapper.readValue(entriesJson, new TypeReference<>() {});
            for (EntryDto dto : entries) {
                if (dto.name == null || dto.name.isBlank()) continue;
                FrameworkEntry entry = new FrameworkEntry();
                entry.setFramework(parseFramework(dto.framework));
                entry.setName(dto.name.trim());
                entry.setUrl(blankToNull(dto.url));
                entry.setGithub(blankToNull(dto.github));
                entry.setDescription(blankToNull(dto.description));
                entry.setType(parseType(dto.type));
                entry.setSince(blankToNull(dto.since));
                c.addEntry(entry);
            }
        } catch (Exception e) {
            // invalid JSON
        }
    }

    private Framework parseFramework(String fw) {
        if (fw == null || fw.isBlank()) return Framework.Spring;
        try {
            return Framework.valueOf(fw);
        } catch (IllegalArgumentException e) {
            return Framework.Spring;
        }
    }

    private FeatureType parseType(String type) {
        if (type == null || type.isBlank()) return FeatureType.SUB_PROJECT;
        try {
            return FeatureType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return FeatureType.SUB_PROJECT;
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    public static class EntryDto {
        public String framework;
        public String name;
        public String url;
        public String github;
        public String description;
        public String type;
        public String since;
    }
}
