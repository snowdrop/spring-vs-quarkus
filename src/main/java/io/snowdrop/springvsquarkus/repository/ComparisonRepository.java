package io.snowdrop.springvsquarkus.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.snowdrop.springvsquarkus.model.Comparison;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class ComparisonRepository implements PanacheRepository<Comparison> {

    public List<Comparison> findAllOrdered() {
        return list("ORDER BY category");
    }

    public List<Comparison> findFiltered(String nameFilter, Boolean springSupported, Boolean quarkusSupported) {
        var query = new StringBuilder("1=1");
        var params = new java.util.HashMap<String, Object>();

        if (nameFilter != null && !nameFilter.isBlank()) {
            query.append(" AND LOWER(category) LIKE LOWER(:name)");
            params.put("name", "%" + nameFilter.trim() + "%");
        }
        if (springSupported != null) {
            if (springSupported) {
                query.append(" AND id IN (SELECT e.comparison.id FROM FrameworkEntry e WHERE e.framework = 'SPRING')");
            } else {
                query.append(" AND id NOT IN (SELECT e.comparison.id FROM FrameworkEntry e WHERE e.framework = 'SPRING')");
            }
        }
        if (quarkusSupported != null) {
            if (quarkusSupported) {
                query.append(" AND id IN (SELECT e.comparison.id FROM FrameworkEntry e WHERE e.framework = 'QUARKUS')");
            } else {
                query.append(" AND id NOT IN (SELECT e.comparison.id FROM FrameworkEntry e WHERE e.framework = 'QUARKUS')");
            }
        }

        return find(query.toString(), io.quarkus.panache.common.Sort.ascending("category"), params).list();
    }

    public long countSpringOnly() {
        return count("id IN (SELECT e.comparison.id FROM FrameworkEntry e WHERE e.framework = 'SPRING') " +
                     "AND id NOT IN (SELECT e.comparison.id FROM FrameworkEntry e WHERE e.framework = 'QUARKUS')");
    }

    public long countQuarkusOnly() {
        return count("id NOT IN (SELECT e.comparison.id FROM FrameworkEntry e WHERE e.framework = 'SPRING') " +
                     "AND id IN (SELECT e.comparison.id FROM FrameworkEntry e WHERE e.framework = 'QUARKUS')");
    }

    public long countBoth() {
        return count("id IN (SELECT e.comparison.id FROM FrameworkEntry e WHERE e.framework = 'SPRING') " +
                     "AND id IN (SELECT e.comparison.id FROM FrameworkEntry e WHERE e.framework = 'QUARKUS')");
    }
}
