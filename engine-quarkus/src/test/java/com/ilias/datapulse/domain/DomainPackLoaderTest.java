package com.ilias.datapulse.domain;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class DomainPackLoaderTest {

    @Inject
    DomainPackLoader domainPackLoader;

    @Test
    void shouldLoadOlistEcommerceDomainPack() {
        DomainPack domainPack = domainPackLoader.getActiveDomainPack();

        assertNotNull(domainPack);
        assertEquals("olist-ecommerce", domainPack.domainId());
        assertTrue(domainPack.allowedTableNames().contains("fact_orders"));
        assertEquals(2, domainPack.metrics().size());

        Set<String> metricNames = domainPack.metrics().stream()
                .map(SemanticMetric::name)
                .collect(Collectors.toSet());
        assertTrue(metricNames.contains("GMV"));
        assertTrue(metricNames.contains("AOV"));
    }
}
