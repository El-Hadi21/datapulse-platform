# DataPulse Platform

Moteur analytique **NL2SQL** (Natural Language to SQL) en Java 21 / Quarkus, **agnostique du domaine métier**.

L'utilisateur pose une question en langage naturel. Un LLM écrit une requête SQL, le backend la **valide**, l'exécute en **lecture seule** sur un entrepôt DuckDB, puis le résultat est reformulé. Le LLM ne calcule rien et n'invente jamais de chiffre : la requête SQL est toujours montrée comme preuve.

> **Statut : projet en construction (Palier 1 sur 6).** Ce README distingue ce qui existe de ce qui est prévu. Voir [Avancement](#avancement).

---

## Principes

1. **Zéro hallucination numérique.** Le LLM génère du SQL, il ne produit jamais de chiffres lui-même.
2. **Sécurité indépendante du LLM.** Toute requête passe par une chaîne de validation en Java (SELECT uniquement, tables autorisées, limite de lignes) avant exécution.
3. **Agnosticisme métier.** Aucune table ni formule métier n'est codée en dur : le moteur lit un **Domain Pack**.
4. **Cloud-native à terme.** Conteneurs, puis déploiement Kubernetes (K3d en local, AWS EKS comme cible).

## Le concept de Domain Pack

Un domaine (e-commerce, hôtellerie, etc.) est décrit par un dossier dans `domain-packs/` :

| Fichier | Rôle | État |
|---|---|---|
| `semantic-dictionary.yaml` | Tables Gold autorisées, description des colonnes, catalogue de KPIs (nom, formule SQL, filtre obligatoire) | Présent pour Olist |
| `business-rules.md` | Contexte métier vectorisé pour le RAG | Prévu (Palier 2) |
| `benchmark.json` | Questions et SQL de référence pour mesurer la fiabilité de l'agent | Prévu (Palier 2) |

Changer de métier doit revenir à changer ce dossier et les données, pas le code Java. Le pack actuel, `olist-ecommerce`, s'appuie sur le dataset public Olist (Kaggle).

## Architecture cible

```text
Dataset Olist (CSV) ─► Rejeu Quarkus ─► Redpanda (Kafka) ─► Bronze ─► Silver ─► Gold (dbt + DuckDB)
                                                                                       │
┌──────────────────────────── engine-quarkus (Java 21) ────────────────────────────────┼──┐
│ API REST (Quarkus REST, OpenAPI)                                                     │  │
│   ├─ Domain Loader : charge le Domain Pack                                           │  │
│   ├─ Orchestrateur IA (Quarkus LangChain4j) : RAG, génération SQL, auto-correction   │  │
│   ├─ Pipeline de sécurité SQL (JSqlParser, Chain of Responsibility)                  │  │
│   │     SelectOnlyRule ─► AllowedTablesRule ─► LimitInjectionRule                    │  │
│   └─ Exécuteur JDBC en lecture seule ────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────────────────┘
                     ▲
                     │ HTTP / JSON
              Frontend d'analyse (prévu, Angular)
```

Seule la partie `engine-quarkus` est en cours de développement. Le détail du plan est dans [`docs/`](docs/).

## Structure du dépôt

```text
datapulse-platform/
├── engine-quarkus/        Backend Java 21 + Quarkus
│   └── src/main/java/com/ilias/datapulse/
│       ├── api/           Endpoints REST
│       ├── domain/        Domain Pack (records Java) et son chargeur
│       ├── security/      Chaîne de validation SQL
│       └── execution/     Exécution JDBC en lecture seule
├── domain-packs/
│   └── olist-ecommerce/   semantic-dictionary.yaml
├── data-warehouse/        Fichiers DuckDB (ignorés par Git, générés localement)
└── docs/                  Documents de cadrage
```

## Stack

| Couche | Technologie | État |
|---|---|---|
| Backend | Java 21, Quarkus (REST, Jackson, OpenAPI, Agroal, config YAML) | En place |
| Entrepôt analytique | DuckDB (driver JDBC `duckdb_jdbc` 1.1.3) | Dépendance ajoutée |
| Analyse SQL | JSqlParser 5.0 | Dépendance ajoutée |
| Lecture du Domain Pack | Jackson YAML | Fonctionnel |
| IA | Quarkus LangChain4j, RAG, Guardrails | Prévu (Palier 2) |
| Streaming et Medallion | Redpanda, dbt-duckdb | Prévu (Palier 3) |
| Frontend | Angular | Prévu (Palier 4) |
| Déploiement | Docker, Kubernetes (K3d), AWS | Prévu (Palier 5) |
| Observabilité | OpenTelemetry, Micrometer, Prometheus, Grafana | Prévu (Palier 6) |

## Démarrage

**Prérequis :** JDK 21, Maven (ou le wrapper `mvnw` fourni).

```bash
git clone https://github.com/El-Hadi21/datapulse-platform.git
cd datapulse-platform/engine-quarkus
./mvnw quarkus:dev        # Windows : mvnw.cmd quarkus:dev
```

Par défaut :

- l'API écoute sur `http://localhost:8080` ;
- le Domain Pack est lu depuis `../domain-packs/olist-ecommerce/semantic-dictionary.yaml` ;
- la base DuckDB est attendue dans `../data-warehouse/olist.duckdb`. Ce fichier n'est **pas versionné** : le jeu de données Olist est à télécharger depuis Kaggle puis à charger dans DuckDB (étape du Palier 1, pas encore documentée ici).

Les chemins étant relatifs, la commande doit être lancée depuis `engine-quarkus/`.

## Avancement

Le développement avance par paliers : on ne passe au suivant que lorsque le précédent est compris et validé.

**Palier 1 : moteur backend et exécution SQL sécurisée** (en cours)

- [x] Projet Quarkus initialisé (Java 21)
- [x] Modèle du Domain Pack en records Java (`DomainPack`, `TableMetadata`, `ColumnMetadata`, `SemanticMetric`)
- [x] `DomainPackLoader` : lecture du dictionnaire sémantique YAML au démarrage
- [x] Dictionnaire sémantique Olist : table `fact_orders`, KPIs GMV et AOV
- [ ] Chargement du dataset Olist dans DuckDB
- [ ] Chaîne de validation SQL : `SqlSecurityRule`, `SelectOnlyRule`, `AllowedTablesRule`, `LimitInjectionRule`, `SqlValidationPipeline` (classes créées, logique à écrire)
- [ ] `ReadOnlySqlExecutor` : exécution JDBC en lecture seule
- [ ] Endpoint `POST /api/query/execute` (`QueryResource` à implémenter)
- [ ] Tests unitaires JUnit 5

**Paliers suivants**

- [ ] **Palier 2** : agent NL2SQL avec Quarkus LangChain4j, RAG sur le dictionnaire, auto-correction par guardrail (3 essais max), benchmark de fiabilité
- [ ] **Palier 3** : rejeu d'événements via Redpanda, couches Bronze/Silver/Gold avec dbt, second Domain Pack pour prouver l'agnosticisme
- [ ] **Palier 4** : interface web (sélecteur de domaine, chat, requête SQL affichée, résultats)
- [ ] **Palier 5** : Kubernetes (K3d), sondes de santé, correspondance AWS
- [ ] **Palier 6** : télémétrie, métriques, tableaux de bord

## Évaluation prévue

Chaque Domain Pack embarquera un jeu de questions de référence (`benchmark.json`). L'agent sera comparé **avant / après** activation du RAG et des guardrails, avec les métriques suivantes : validité SQL, exactitude du résultat, taux de colonnes inventées, taux de refus pertinent, latence médiane. Les résultats seront publiés ici.

## Documentation

- [Document de cadrage v3](docs/) : vision, principes, architecture et roadmap détaillée.

## Licence

À définir.