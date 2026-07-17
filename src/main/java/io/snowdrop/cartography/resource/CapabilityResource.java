package io.snowdrop.cartography.resource;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.snowdrop.cartography.model.Capability;
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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@Path("/capabilities")
public class CapabilityResource {

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance list(List<Capability> capabilities, String nameFilter, boolean saved);
        public static native TemplateInstance form(Capability capability, boolean isNew);
    }

    @Inject
    RegistryStore store;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance list(
            @QueryParam("name") String name,
            @QueryParam("saved") String saved) {
        List<Capability> capabilities;
        if (name == null || name.isBlank()) {
            capabilities = store.findAllOrdered();
        } else {
            capabilities = store.findFiltered(name, null, null);
        }
        return Templates.list(capabilities, name != null ? name : "", "true".equals(saved));
    }

    @GET
    @Path("/new")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance newForm() {
        return Templates.form(new Capability(""), true);
    }

    @GET
    @Path("/{id}/edit")
    @Produces(MediaType.TEXT_HTML)
    public Response editForm(@PathParam("id") Long id) {
        Capability c = store.findById(id);
        if (c == null) {
            return Response.seeOther(URI.create("/capabilities")).build();
        }
        return Response.ok(Templates.form(c, false)).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response create(
            @FormParam("category") String category,
            @FormParam("description") String description,
            @FormParam("tags") String tags) {
        Capability c = new Capability(category);
        c.setDescription(blankToNull(description));
        c.setTags(blankToNull(tags));
        store.persist(c);
        return Response.seeOther(URI.create("/capabilities")).build();
    }

    @POST
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response update(
            @PathParam("id") Long id,
            @FormParam("category") String category,
            @FormParam("description") String description,
            @FormParam("tags") String tags,
            @FormParam("reviewBy") String reviewBy,
            @FormParam("reviewDate") String reviewDate,
            @FormParam("quarkusStatus") String quarkusStatus,
            @FormParam("statusComment") String statusComment) {
        Capability c = store.findById(id);
        if (c == null) {
            return Response.seeOther(URI.create("/capabilities")).build();
        }
        c.setCategory(category);
        c.setDescription(blankToNull(description));
        c.setTags(blankToNull(tags));
        c.setReviewBy(blankToNull(reviewBy));
        c.setReviewDate(parseDate(reviewDate));
        c.setQuarkusStatus(blankToNull(quarkusStatus));
        c.setStatusComment(blankToNull(statusComment));
        store.update(c);
        return Response.seeOther(URI.create("/capabilities")).build();
    }

    @POST
    @Path("/{id}/delete")
    public Response delete(@PathParam("id") Long id) {
        Capability capability = store.findById(id);
        if (c == null || !c.getEntries().isEmpty()) {
            return Response.seeOther(URI.create("/capabilities")).build();
        }
        store.deleteById(id);
        return Response.seeOther(URI.create("/capabilities")).build();
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
}
