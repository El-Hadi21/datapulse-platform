package com.ilias.datapulse.security;

import com.ilias.datapulse.domain.DomainPack;
import jakarta.enterprise.context.ApplicationScoped;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.jboss.logging.Logger;

import java.util.Locale;
import java.util.Set;

@ApplicationScoped
public class AllowedTablesRule implements SqlSecurityRule {

    private static final Logger LOG = Logger.getLogger(AllowedTablesRule.class);
    private static final String DEFAULT_SCHEMA = "main";

    @Override
    public void apply(Statement statement, DomainPack domainPack) {
        Set<String> allowedTables = domainPack == null
                ? Set.of()
                : domainPack.allowedTableNames();

        TablesNamesFinder<Void> tablesNamesFinder = new TablesNamesFinder<>();
        Set<String> tableNames = tablesNamesFinder.getTables(statement);

        if (tableNames == null || tableNames.isEmpty()) {
            LOG.warn("[DATAPULSE] Requete SQL rejetee : aucune table autorisee detectee.");
            throw new SqlSecurityException(
                    "La requete doit cibler au moins une table autorisee du Domain Pack.");
        }

        for (String tableName : tableNames) {
            String normalizedTableName = extractAndValidateTableName(tableName);

            if (!allowedTables.contains(normalizedTableName)) {
                LOG.warnf("[DATAPULSE] Table SQL interdite : %s", normalizedTableName);
                throw new SqlSecurityException("Table SQL interdite : " + normalizedTableName);
            }
        }
    }

    private String extractAndValidateTableName(String rawTableName) {
        String cleaned = rawTableName.trim()
                .replace("`", "")
                .replace("\"", "")
                .replace("[", "")
                .replace("]", "")
                .replace("'", "")
                .toLowerCase(Locale.ROOT);

        int schemaSeparator = cleaned.lastIndexOf('.');
        if (schemaSeparator >= 0) {
            String schema = cleaned.substring(0, schemaSeparator);
            if (!DEFAULT_SCHEMA.equals(schema)) {
                LOG.warnf("[DATAPULSE] Schema SQL interdit : %s", schema);
                throw new SqlSecurityException("Schema SQL interdit : " + schema);
            }
            return cleaned.substring(schemaSeparator + 1);
        }

        return cleaned;
    }
}