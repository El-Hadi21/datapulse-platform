package com.ilias.datapulse.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.File;
import java.io.IOException;

@ApplicationScoped
public class DomainPackLoader {

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

            // On pointe vers le fichier YAML que tu as créé dans ton monorepo
            File yamlFile = new File("../domain-packs/olist-ecommerce/semantic-dictionary.yaml");

            // Désérialisation magique : le YAML est transformé en nos Records Java 21
            this.activeDomainPack = mapper.readValue(yamlFile, DomainPack.class);

            System.out.println("✅ [DATAPULSE] Domain Pack chargé avec succès : " + activeDomainPack.displayName());
            System.out.println("✅ [DATAPULSE] Nombre de tables autorisées : " + activeDomainPack.tables().size());

        } catch (IOException e) {
            throw new RuntimeException("❌ [DATAPULSE] Impossible de charger le Domain Pack YAML", e);
        }
    }

    public DomainPack getActiveDomainPack() {
        return activeDomainPack;
    }
}