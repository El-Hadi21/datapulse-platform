package com.ilias.datapulse.domain;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record DomainPack(
        String domainId,
        String displayName,
        String description,
        List<TableMetadata> tables,
        List<SemanticMetric> metrics
) {
    /**
     * Retourne l'ensemble des noms de tables autorisées (en minuscules)
     * pour alimenter notre future règle de sécurité AllowedTablesRule.
     */
    public Set<String> allowedTableNames() {
        if (tables == null) {
            return Set.of();
        }
        return tables.stream()
                .map(t -> t.name().toLowerCase())
                .collect(Collectors.toUnmodifiableSet());
    }
}
