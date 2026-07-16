package io.snowdrop.cartography.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.snowdrop.cartography.model.FrameworkEntry;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FrameworkEntryRepository implements PanacheRepository<FrameworkEntry> {
}
