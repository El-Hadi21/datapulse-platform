package com.ilias.datapulse.security;

import com.ilias.datapulse.domain.DomainPack;
import net.sf.jsqlparser.statement.Statement;

public interface SqlSecurityRule {
    void apply(Statement statement, DomainPack domainPack);
}
