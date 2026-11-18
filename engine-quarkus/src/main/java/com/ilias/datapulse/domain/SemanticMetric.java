package com.ilias.datapulse.domain;

public record SemanticMetric(
        String name,
        String displayName,
        String description,
        String sqlFormula,
        String requiredFilter
) {}