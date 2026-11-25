package com.ilias.datapulse.security;

import com.ilias.datapulse.domain.DomainPack;
import jakarta.enterprise.context.ApplicationScoped;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.Select;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class LimitInjectionRule implements SqlSecurityRule {

    static final long MAX_LIMIT = 100L;

    private static final Logger LOG = Logger.getLogger(LimitInjectionRule.class);

    @ConfigProperty(name = "datapulse.security.max-limit", defaultValue = "100")
    long maxLimit = MAX_LIMIT;

    @Override
    public void apply(Statement statement, DomainPack domainPack) {
        if (!(statement instanceof Select select)) {
            return;
        }

        Limit limit = select.getLimit();

        if (limit == null || limit.isLimitAll() || limit.isLimitNull() || limit.getRowCount() == null) {
            Limit sanitizedLimit = new Limit();
            sanitizedLimit.setRowCount(new LongValue(maxLimit));
            if (limit != null && limit.getOffset() != null) {
                sanitizedLimit.setOffset(limit.getOffset());
            }
            select.setLimit(sanitizedLimit);
            LOG.debugf("[DATAPULSE] LIMIT force automatiquement a : %d", maxLimit);
            return;
        }

        if (!(limit.getRowCount() instanceof LongValue rowCount)) {
            LOG.warnf("[DATAPULSE] Expression LIMIT non numerique remplacee par : %d", maxLimit);
            limit.setRowCount(new LongValue(maxLimit));
            return;
        }

        if (rowCount.getValue() <= 0 || rowCount.getValue() > maxLimit) {
            LOG.warnf("[DATAPULSE] LIMIT plafonne : %d -> %d", rowCount.getValue(), maxLimit);
            limit.setRowCount(new LongValue(maxLimit));
        }
    }
}