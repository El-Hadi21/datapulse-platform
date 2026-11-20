package com.ilias.datapulse.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.File;
import java.io.IOException;

@ApplicationScoped
public class DomainPackLoader {

    private static final Logger LOG = Logger.getLogger(DomainPackLoader.class);

    @ConfigProperty(name = "datapulse.domain.pack-path")
    String domainPackPath;

    private DomainPack activeDomainPack;

    // @PostConstruct indique à Quarkus d'exécuter cette méthode
    // automatiquement juste après la création de ce composant.
    @PostConstruct
    void init() {
        loadDomainPack();
    }

    private void loadDomainPack() {
        try {
            // On configure Jackson pour lire du format YAML au lieu du JSON classique
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

            File yamlFile = new File(domainPackPath);

            if (!yamlFile.exists()) {
                throw new IllegalStateException(
                        "Le fichier de Domain Pack est introuvable : " + yamlFile.getAbsolutePath());
            }

            // Désérialisation magique : le YAML est transformé en nos Records Java 21
            this.activeDomainPack = mapper.readValue(yamlFile, DomainPack.class);

            LOG.infof("[DATAPULSE] Domain Pack charge avec succes : %s", activeDomainPack.displayName());
            LOG.infof("[DATAPULSE] Nombre de tables autorisees : %d",
                    activeDomainPack.allowedTableNames().size());

        } catch (IOException e) {
            LOG.errorf(e, "[DATAPULSE] Impossible de charger le Domain Pack YAML");
            throw new RuntimeException("[DATAPULSE] Impossible de charger le Domain Pack YAML", e);
        }
    }

    public DomainPack getActiveDomainPack() {
        return activeDomainPack;
    }
}