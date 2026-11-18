package com.ilias.datapulse.domain;

import java.util.List;

public record TableMetadata(
        String name,
        String description,
        List<ColumnMetadata> columns
) {}