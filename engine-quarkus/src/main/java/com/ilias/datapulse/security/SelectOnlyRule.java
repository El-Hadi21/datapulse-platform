package com.ilias.datapulse.security;

import com.ilias.datapulse.domain.DomainPack;
import jakarta.enterprise.context.ApplicationScoped;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SelectOnlyRule implements SqlSecurityRule {

    private static final Logger LOG = Logger.getLogger(SelectOnlyRule.class);

    @Override
    public void apply(Statement statement, DomainPack domainPack) {
        if (!(statement instanceof Select)) {
            String statementType = statement == null
                    ? "null"
                    : statement.getClass().getSimpleName();

            LOG.warnf("[DATAPULSE] Instruction SQL interdite : type detecte = %s", statementType);
            throw new SqlSecurityException("Seules les instructions SELECT sont autorisées.");
        }

        if (statement instanceof PlainSelect plainSelect
                && plainSelect.getIntoTables() != null
                && !plainSelect.getIntoTables().isEmpty()) {
            LOG.warn("[DATAPULSE] Instruction SELECT INTO interdite.");
            throw new SqlSecurityException("Les instructions SELECT INTO sont interdites.");
        }
    }
}