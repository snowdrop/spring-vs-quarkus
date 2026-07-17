package io.snowdrop.cartography.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "capability")
public class Capability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String category;

    @Column(length = 1024)
    private String description;

    @Column(name = "tags")
    private String tags;

    private String topic;

    @Column(name = "review_by")
    private String reviewBy;

    @Column(name = "review_date")
    private LocalDate reviewDate;

    @Column(name = "quarkus_status")
    private String quarkusStatus;

    @Column(name = "status_comment", length = 1024)
    private String statusComment;

    @OneToMany(mappedBy = "capability", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
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
        entry.setCapability(this);
    }

    public void removeEntry(FrameworkEntry entry) {
        entries.remove(entry);
        entry.setCapability(null);
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
