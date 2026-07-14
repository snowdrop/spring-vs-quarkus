package io.snowdrop.springvsquarkus.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "comparison")
public class Comparison {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String category;

    @Column(length = 1024)
    private String description;

    @Column(name = "tags")
    private String tags;

    @OneToMany(mappedBy = "comparison", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<FrameworkEntry> entries = new ArrayList<>();

    public Comparison() {
    }

    public Comparison(String category) {
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

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public List<FrameworkEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<FrameworkEntry> entries) {
        this.entries = entries;
    }

    public void addEntry(FrameworkEntry entry) {
        entries.add(entry);
        entry.setComparison(this);
    }

    public void removeEntry(FrameworkEntry entry) {
        entries.remove(entry);
        entry.setComparison(null);
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