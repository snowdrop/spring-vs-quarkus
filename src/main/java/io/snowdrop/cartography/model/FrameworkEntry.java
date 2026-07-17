package io.snowdrop.cartography.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class FrameworkEntry {

    private Long id;
    private Framework framework;
    private String name;
    private String doc;
    private String scm;
    private String description;
    private ComponentType type;
    private String since;
    private String comment;

    public FrameworkEntry() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Framework getFramework() {
        return framework;
    }

    public void setFramework(Framework framework) {
        this.framework = framework;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDoc() {
        return doc;
    }

    public void setDoc(String doc) {
        this.doc = doc;
    }

    public String getScm() {
        return scm;
    }

    @JsonIgnore
    public String getScmRepoName() {
        if (scm == null || scm.isEmpty()) return "";
        String trimmed = scm.endsWith("/") ? scm.substring(0, scm.length() - 1) : scm;
        int lastSlash = trimmed.lastIndexOf('/');
        return lastSlash >= 0 ? trimmed.substring(lastSlash + 1) : trimmed;
    }

    public void setScm(String scm) {
        this.scm = scm;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ComponentType getType() {
        return type;
    }

    public void setType(ComponentType type) {
        this.type = type;
    }

    public String getSince() {
        return since;
    }

    public void setSince(String since) {
        this.since = since;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
