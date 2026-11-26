package com.ilias.datapulse.security;

import com.ilias.datapulse.domain.DomainPack;
import com.ilias.datapulse.domain.DomainPackLoader;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;

import java.util.List;

@ApplicationScoped
public class SqlValidationPipeline {

    private final DomainPackLoader domainPackLoader;
    private final List<SqlSecurityRule> rules;

    @Inject
    public SqlValidationPipeline(
            DomainPackLoader domainPackLoader,
            SelectOnlyRule selectOnlyRule,
            AllowedTablesRule allowedTablesRule,
            LimitInjectionRule limitInjectionRule) {
        this.domainPackLoader = domainPackLoader;
        this.rules = List.of(selectOnlyRule, allowedTablesRule, limitInjectionRule);
    }

    public String validateAndSanitize(String rawSql) {
        if (rawSql == null || rawSql.isBlank()) {
            throw new SqlSecurityException(
                    "La requete SQL est obligatoire et ne peut pas etre vide.");
        }

        final Statements statements;
        try {
            statements = CCJSqlParserUtil.parseStatements(rawSql);
        } catch (JSQLParserException e) {
            throw new SqlSecurityException("Requete SQL invalide : " + e.getMessage(), e);
        }

        if (statements == null || statements.size() != 1) {
            throw new SqlSecurityException(
                    "Une seule instruction SQL est autorisee par requete.");
        }

        Statement statement = statements.getFirst();

        DomainPack domainPack = domainPackLoader.getActiveDomainPack();
        for (SqlSecurityRule rule : rules) {
            rule.apply(statement, domainPack);
        }

        return statement.toString();
    }
}
