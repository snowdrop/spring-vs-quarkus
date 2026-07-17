package io.snowdrop.cartography.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Capability {

    private Long id;
    private String category;
    private String description;
    private String tags;
    private String topic;
    private String reviewBy;
    private LocalDate reviewDate;
    private String quarkusStatus;
    private String statusComment;
    private List<FrameworkEntry> entries = new ArrayList<>();

    public Capability() {
    }

    public Capability(String category) {
        this.category = category;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public String getShortDescription() {
        if (description == null) return null;
        return description.length() > 150 ? description.substring(0, 150) + "..." : description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getReviewBy() {
        return reviewBy;
    }

    public void setReviewBy(String reviewBy) {
        this.reviewBy = reviewBy;
    }

    public LocalDate getReviewDate() {
        return reviewDate;
    }

    public void setReviewDate(LocalDate reviewDate) {
        this.reviewDate = reviewDate;
    }

    public String getQuarkusStatus() {
        return quarkusStatus;
    }

    public void setQuarkusStatus(String quarkusStatus) {
        this.quarkusStatus = quarkusStatus;
    }

    public String getStatusComment() {
        return statusComment;
    }

    public void setStatusComment(String statusComment) {
        this.statusComment = statusComment;
    }

    public List<FrameworkEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<FrameworkEntry> entries) {
        this.entries = entries;
    }

    public void addEntry(FrameworkEntry entry) {
        entries.add(entry);
    }

    public void removeEntry(FrameworkEntry entry) {
        entries.remove(entry);
    }

    public boolean isSpringSupported() {
        return entries.stream().anyMatch(e -> e.getFramework() == Framework.Spring);
    }

    public boolean isQuarkusSupported() {
        return entries.stream().anyMatch(e -> e.getFramework() == Framework.Quarkus);
    }

    public List<FrameworkEntry> getSpringEntries() {
        return entries.stream().filter(e -> e.getFramework() == Framework.Spring).toList();
    }

    public List<FrameworkEntry> getQuarkusEntries() {
        return entries.stream().filter(e -> e.getFramework() == Framework.Quarkus).toList();
    }
}
