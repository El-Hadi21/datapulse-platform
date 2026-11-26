package com.ilias.datapulse.security;

import com.ilias.datapulse.domain.DomainPack;
import com.ilias.datapulse.domain.DomainPackLoader;
import com.ilias.datapulse.domain.TableMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SqlValidationPipelineTest {

    @Mock
    DomainPackLoader domainPackLoader;

    private SqlValidationPipeline pipeline;

    @BeforeEach
    void setUp() {
        DomainPack testPack = new DomainPack(
                "olist-test",
                "Olist Test Pack",
                "Domain pack de test pour la sécurité SQL",
                List.of(
                        new TableMetadata("fact_orders", "Commandes", List.of()),
                        new TableMetadata("dim_customers", "Clients", List.of())
                ),
                List.of()
        );

        lenient().when(domainPackLoader.getActiveDomainPack()).thenReturn(testPack);

        pipeline = new SqlValidationPipeline(
                domainPackLoader,
                new SelectOnlyRule(),
                new AllowedTablesRule(),
                new LimitInjectionRule()
        );
    }

    // --- 1. CAS PASSANTS (REQUÊTES LÉGITIMES) ---

    @Test
    void shouldInjectDefaultLimitWhenMissing() {
        String sanitized = pipeline.validateAndSanitize("SELECT order_id, payment_value FROM fact_orders");

        assertTrue(sanitized.toUpperCase().endsWith("LIMIT 100"));
    }

    @Test
    void shouldKeepValidLowerLimit() {
        String sanitized = pipeline.validateAndSanitize("SELECT order_id FROM fact_orders LIMIT 20");

        assertTrue(sanitized.toUpperCase().endsWith("LIMIT 20"));
    }

    @Test
    void shouldAllowJoinOnAuthorizedTablesWithDefaultSchemaAndCaseInsensitivity() {
        String sql = "SELECT o.order_id FROM main.\"FACT_ORDERS\" o JOIN dim_customers c ON o.customer_id = c.customer_id";

        String sanitized = pipeline.validateAndSanitize(sql);

        assertTrue(sanitized.toUpperCase().endsWith("LIMIT 100"));
    }

    // --- 2. ENTRÉES INVALIDES & INJECTIONS MULTI-REQUÊTES ---

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void shouldRejectNullOrBlankSql(String invalidSql) {
        assertThrows(SqlSecurityException.class, () -> pipeline.validateAndSanitize(invalidSql));
    }

    @Test
    void shouldRejectInvalidSyntax() {
        assertThrows(SqlSecurityException.class,
                () -> pipeline.validateAndSanitize("SELEC * FORM fact_orders"));
    }

    @Test
    void shouldRejectStackedQueriesInjection() {
        String injection = "SELECT * FROM fact_orders; DROP TABLE fact_orders";

        assertThrows(SqlSecurityException.class, () -> pipeline.validateAndSanitize(injection));
    }

    // --- 3. RÈGLE SELECT ONLY ---

    @ParameterizedTest
    @ValueSource(strings = {
            "DROP TABLE fact_orders",
            "DELETE FROM fact_orders WHERE order_id = '1'",
            "UPDATE fact_orders SET payment_value = 0",
            "INSERT INTO fact_orders (order_id) VALUES ('999')",
            "ALTER TABLE fact_orders ADD COLUMN hacked VARCHAR",
            "CREATE TABLE evil (id INT)"
    })
    void shouldRejectNonSelectStatements(String forbiddenSql) {
        assertThrows(SqlSecurityException.class, () -> pipeline.validateAndSanitize(forbiddenSql));
    }

    @Test
    void shouldRejectSelectIntoStatement() {
        String selectInto = "SELECT * INTO hacked_table FROM fact_orders";

        assertThrows(SqlSecurityException.class, () -> pipeline.validateAndSanitize(selectInto));
    }

    // --- 4. RÈGLE ALLOWED TABLES & SCHÉMAS ---

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM secret_users",
            "SELECT * FROM fact_orders JOIN secret_users ON fact_orders.customer_id = secret_users.id",
            "SELECT * FROM fact_orders WHERE customer_id IN (SELECT id FROM secret_users)"
    })
    void shouldRejectUnauthorizedTablesEvenInJoinsOrSubqueries(String sqlWithForbiddenTable) {
        assertThrows(SqlSecurityException.class, () -> pipeline.validateAndSanitize(sqlWithForbiddenTable));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM read_csv('/etc/passwd')",
            "SELECT * FROM duckdb_tables()",
            "SELECT 1"
    })
    void shouldRejectQueriesWithoutAuthorizedTablesOrUsingDuckDbFunctions(String systemFunctionSql) {
        assertThrows(SqlSecurityException.class, () -> pipeline.validateAndSanitize(systemFunctionSql));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM information_schema.tables",
            "SELECT * FROM other_schema.fact_orders"
    })
    void shouldRejectUnauthorizedSchemas(String forbiddenSchemaSql) {
        assertThrows(SqlSecurityException.class, () -> pipeline.validateAndSanitize(forbiddenSchemaSql));
    }

    // --- 5. RÈGLE LIMIT INJECTION & CONTOURNEMENTS ---

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM fact_orders LIMIT 5000",
            "SELECT * FROM fact_orders LIMIT ALL",
            "SELECT * FROM fact_orders LIMIT 500 + 500",
            "SELECT * FROM fact_orders LIMIT 0",
            "SELECT * FROM fact_orders LIMIT -10",
            "SELECT order_id FROM fact_orders UNION SELECT order_id FROM fact_orders"
    })
    void shouldEnforceMaxLimitOnBypassAttempts(String bypassSql) {
        String sanitized = pipeline.validateAndSanitize(bypassSql);

        assertTrue(sanitized.toUpperCase().endsWith("LIMIT 100"),
                "Le SQL nettoyé devrait se terminer par LIMIT 100 mais vaut : " + sanitized);
    }
}