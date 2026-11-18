package com.ilias.datapulse.domain;

public record ColumnMetadata(
        String name,
        String dataType,
        String description
) {}