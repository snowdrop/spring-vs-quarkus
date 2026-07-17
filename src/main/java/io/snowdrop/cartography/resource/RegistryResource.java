package io.snowdrop.cartography.resource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.snowdrop.cartography.model.Capability;
import io.snowdrop.cartography.model.ComponentType;
import io.snowdrop.cartography.model.Framework;
import io.snowdrop.cartography.model.FrameworkEntry;
import io.snowdrop.cartography.service.CsvService;
import io.snowdrop.cartography.service.ExcelService;
import io.snowdrop.cartography.service.MarkdownService;
import io.snowdrop.cartography.service.RegistryEnrichmentService;
import io.snowdrop.cartography.store.RegistryStore;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.quarkus.qute.RawString;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Path("/")
public class RegistryResource {

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance list(
                List<Capability> capabilities,
                long totalCount,
                long bothCount,
                long springOnlyCount,
                long quarkusOnlyCount,
                String nameFilter,
                String springFilter,
                String quarkusFilter,
                boolean saved);

        public static native TemplateInstance form(
                Capability capability,
                boolean isNew,
                List<ComponentType> componentTypes,
                List<Framework> frameworks,
                List<Capability> allCapabilities,
                RawString entriesJson);
    }

    @Inject
    RegistryStore store;

    @Inject
    CsvService csvService;

    @Inject
    MarkdownService markdownService;

    @Inject
    ExcelService excelService;

    @Inject
    RegistryEnrichmentService enrichmentService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response root() {
        return Response.seeOther(URI.create("/registry")).build();
    }

    @GET
    @Path("/registry")
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

        List<Capability> capabilities;
        if ((name == null || name.isBlank()) && springSupported == null && quarkusSupported == null) {
            capabilities = store.findAllOrdered();
        } else {
            capabilities = store.findFiltered(name, springSupported, quarkusSupported);
        }

        return Templates.list(
                capabilities,
                store.count(),
                store.countBoth(),
                store.countSpringOnly(),
                store.countQuarkusOnly(),
                name != null ? name : "",
                spring != null ? spring : "",
                quarkus != null ? quarkus : "",
                "true".equals(saved));
    }

    @GET
    @Path("/registry/new")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance newForm() {
        return Templates.form(new Capability(""), true,
                List.of(ComponentType.values()), List.of(Framework.values()),
                List.of(), new RawString("[]"));
    }

    @GET
    @Path("/registry/{id}/edit")
    @Produces(MediaType.TEXT_HTML)
    public Response editForm(@PathParam("id") Long id) {
        Capability c = store.findById(id);
        if (c == null) {
            return Response.seeOther(URI.create("/registry")).build();
        }
        return Response.ok(Templates.form(c, false,
                List.of(ComponentType.values()), List.of(Framework.values()),
                store.findAllOrdered(), serializeEntries(c))).build();
    }

    @POST
    @Path("/registry")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response create(
            @FormParam("category") String category,
            @FormParam("description") String description,
            @FormParam("tags") String tags,
            @FormParam("entriesJson") String entriesJson) {

        Capability c = new Capability(category);
        c.setDescription(blankToNull(description));
        c.setTags(blankToNull(tags));
        applyEntries(c, entriesJson);
        store.persist(c);
        return Response.seeOther(URI.create("/registry/" + c.getId() + "/edit")).build();
    }

    @POST
    @Path("/registry/{id}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response update(
            @PathParam("id") Long id,
            @FormParam("category") String category,
            @FormParam("description") String description,
            @FormParam("tags") String tags,
            @FormParam("reviewBy") String reviewBy,
            @FormParam("reviewDate") String reviewDate,
            @FormParam("quarkusStatus") String quarkusStatus,
            @FormParam("statusComment") String statusComment,
            @FormParam("entriesJson") String entriesJson) {

        Capability c = store.findById(id);
        if (c == null) {
            return Response.seeOther(URI.create("/registry")).build();
        }

        c.setCategory(category);
        c.setDescription(blankToNull(description));
        c.setTags(blankToNull(tags));
        c.setReviewBy(blankToNull(reviewBy));
        c.setReviewDate(parseDate(reviewDate));
        c.setQuarkusStatus(blankToNull(quarkusStatus));
        c.setStatusComment(blankToNull(statusComment));
        Capability updated = new Capability(c.getCategory());
        updated.setId(c.getId());
        updated.setDescription(c.getDescription());
        updated.setTags(c.getTags());
        updated.setReviewBy(c.getReviewBy());
        updated.setReviewDate(c.getReviewDate());
        updated.setQuarkusStatus(c.getQuarkusStatus());
        updated.setStatusComment(c.getStatusComment());
        applyEntries(updated, entriesJson);
        store.update(updated);
        return Response.seeOther(URI.create("/registry/" + id + "/edit")).build();
    }

    @POST
    @Path("/registry/{id}/quarkus-status")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateQuarkusStatus(
            @PathParam("id") Long id,
            @FormParam("status") String status) {
        Capability c = store.findById(id);
        if (c == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        c.setQuarkusStatus(blankToNull(status));
        store.update(c);
        return Response.ok(Map.of("id", id, "quarkusStatus", status != null ? status : "")).build();
    }

    @POST
    @Path("/registry/{id}/delete")
    public Response delete(@PathParam("id") Long id) {
        store.deleteById(id);
        return Response.seeOther(URI.create("/registry")).build();
    }

    @POST
    @Path("/registry/{id}/entries/{entryId}/move")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response moveEntry(
            @PathParam("id") Long capabilityId,
            @PathParam("entryId") Long entryId,
            @FormParam("targetCapabilityId") Long targetCapabilityId) {

        if (targetCapabilityId == null || targetCapabilityId.equals(capabilityId)) {
            return Response.seeOther(URI.create("/registry/" + capabilityId + "/edit")).build();
        }

        Capability target = store.findById(targetCapabilityId);
        if (target == null) {
            return Response.seeOther(URI.create("/registry/" + capabilityId + "/edit")).build();
        }

        store.moveEntry(entryId, capabilityId, targetCapabilityId);
        return Response.seeOther(URI.create("/registry/" + capabilityId + "/edit")).build();
    }

    @GET
    @Path("/export/csv")
    @Produces("text/csv")
    public Response exportCsv() {
        String csv = csvService.exportCsv();
        return Response.ok(csv)
                .header("Content-Disposition", "attachment; filename=\"spring-quarkus-comparison.csv\"")
                .build();
    }

    @GET
    @Path("/export/gsheet")
    @Produces("text/csv")
    public Response exportGSheetCsv() {
        String csv = csvService.exportGSheetCsv();
        return Response.ok(csv)
                .header("Content-Disposition", "attachment; filename=\"spring-quarkus-comparison-gsheet.csv\"")
                .build();
    }

    @GET
    @Path("/export/markdown")
    @Produces("text/markdown")
    public Response exportMarkdown() {
        String md = markdownService.exportMarkdown();
        return Response.ok(md)
                .header("Content-Disposition", "attachment; filename=\"spring-quarkus-comparison.md\"")
                .build();
    }

    @GET
    @Path("/export/xlsx")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response exportXlsx() throws java.io.IOException {
        byte[] xlsx = excelService.exportXlsx();
        return Response.ok(xlsx)
                .header("Content-Disposition", "attachment; filename=\"spring-quarkus-comparison.xlsx\"")
                .build();
    }

    @POST
    @Path("/save")
    public Response saveToYaml(@HeaderParam("Referer") String referer) {
        store.saveToYaml();
        String redirect = "/registry?saved=true";
        if (referer != null && referer.contains("/capabilities")) {
            redirect = "/capabilities?saved=true";
        }
        return Response.seeOther(URI.create(redirect)).build();
    }

    @POST
    @Path("/enrich")
    @Produces(MediaType.APPLICATION_JSON)
    public Response enrichFromRegistry() {
        var result = enrichmentService.enrich();
        return Response.ok(result).build();
    }

    private void applyEntries(Capability c, String entriesJson) {
        if (entriesJson == null || entriesJson.isBlank()) return;
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<EntryDto> entries = mapper.readValue(entriesJson, new TypeReference<>() {});
            for (EntryDto dto : entries) {
                if (dto.name == null || dto.name.isBlank()) continue;
                FrameworkEntry entry = new FrameworkEntry();
                entry.setFramework(parseFramework(dto.framework));
                entry.setName(dto.name.trim());
                entry.setDoc(blankToNull(dto.doc));
                entry.setScm(blankToNull(dto.scm));
                entry.setDescription(blankToNull(dto.description));
                entry.setType(parseType(dto.type));
                entry.setSince(blankToNull(dto.since));
                entry.setComment(blankToNull(dto.comment));
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

    private ComponentType parseType(String type) {
        if (type == null || type.isBlank()) return ComponentType.EXTENSION;
        try {
            return ComponentType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return ComponentType.EXTENSION;
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private RawString serializeEntries(Capability c) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<EntryDto> dtos = c.getEntries().stream().map(e -> {
                EntryDto dto = new EntryDto();
                dto.id = e.getId();
                dto.framework = e.getFramework() != null ? e.getFramework().name() : "Spring";
                dto.name = e.getName();
                dto.doc = e.getDoc();
                dto.scm = e.getScm();
                dto.description = e.getDescription();
                dto.type = e.getType() != null ? e.getType().name() : "";
                dto.since = e.getSince();
                dto.comment = e.getComment();
                return dto;
            }).toList();
            return new RawString(mapper.writeValueAsString(dtos));
        } catch (Exception e) {
            return new RawString("[]");
        }
    }

    public static class EntryDto {
        public Long id;
        public String framework;
        public String name;
        public String doc;
        public String scm;
        public String description;
        public String type;
        public String since;
        public String comment;
    }
}
