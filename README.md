# DataPulse Platform

Moteur analytique **NL2SQL** (Natural Language to SQL) en Java 21 / Quarkus, **agnostique du domaine métier**.

L'utilisateur pose une question en langage naturel. Un LLM écrit une requête SQL, le backend la **valide**, l'exécute en **lecture seule** sur un entrepôt **PostgreSQL**, puis le résultat est reformulé. Le LLM ne calcule rien et n'invente jamais de chiffre : la requête SQL est toujours montrée comme preuve.

> **Statut : projet en construction (Palier 1 sur 6 pour le MVP).** Ce README distingue ce qui existe de ce qui est prévu. Voir [Avancement](#avancement).

---

## Principes

1. **Zéro hallucination numérique.** Le LLM génère du SQL, il ne produit jamais de chiffres lui-même.
2. **Sécurité indépendante du LLM.** Toute requête passe par une chaîne de validation en Java (SELECT uniquement, tables autorisées, limite de lignes) avant exécution. Le moteur exécute la requête validée et réécrite, jamais la chaîne reçue.
3. **Lecture seule.** Le moteur n'écrit jamais dans les données métier.
4. **Agnosticisme métier.** Aucune table ni formule métier n'est codée en dur : le moteur lit un **Domain Pack**.
5. **Cloud-native à terme.** Conteneurs, puis déploiement Kubernetes (K3d en local, AWS EKS comme cible).

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
Dataset Olist (CSV) ─► Rejeu Quarkus ─► Redpanda (Kafka) ─► Bronze ─► Silver ─► Gold (dbt + PostgreSQL)
                                                                                       │
┌──────────────────────────── engine-quarkus (Java 21) ────────────────────────────────┼──┐
│ API REST (Quarkus REST, OpenAPI)                                                     │  │
│   ├─ Domain Loader : charge le Domain Pack                                           │  │
│   ├─ Orchestrateur IA (Quarkus LangChain4j) : RAG (pgvector), SQL, auto-correction   │  │
│   ├─ Pipeline de sécurité SQL (JSqlParser, Chain of Responsibility)                  │  │
│   │     SelectOnlyRule ─► AllowedTablesRule ─► (TenantScopeRule*) ─► LimitInjectionRule │
│   └─ Exécuteur JDBC : rôle PostgreSQL en lecture seule, schéma Gold ─────────────────┘  │
└────────────────────────────────────────────────────────────────────────────────────────┘
                     ▲
                     │ HTTP / JSON
              Frontend d'analyse (prévu, Angular)

* piste d'extension, voir « Périmètre et pistes d'extension »
```

Seule la partie `engine-quarkus` est en cours de développement. Le détail du plan est dans [`docs/`](docs/).

## Pourquoi PostgreSQL

Sur le volume d'Olist (environ 100 000 commandes), un moteur analytique embarqué comme DuckDB aurait aussi répondu vite. PostgreSQL a été retenu pour l'évolutivité et le réalisme :

- **sécurité par ligne** native, utile pour l'accès par tenant (voir plus bas) ;
- **plusieurs écrivains et utilisateurs** en parallèle, nécessaire dès que le flux temps réel alimente la base pendant que l'API la lit ;
- **`pgvector`** pour le RAG, sans composant vectoriel supplémentaire ;
- chemin direct vers **AWS (RDS)** et plusieurs réplicas de l'API en Kubernetes.

Contrepartie : un service à lancer (Docker) et un rôle en lecture seule à configurer. Le code Java reste écrit en SQL portable pour ne pas dépendre d'un SGBD précis.

## Périmètre et pistes d'extension

**Dans le périmètre :** questions-réponses analytiques en **lecture seule** (KPIs, agrégations, comparaisons dans le temps, listes filtrées, tops, détails), par requêtes `SELECT` validées et mesurées par un benchmark.

**Pistes d'extension (hors MVP, non engagées) :**

| Piste | Nature | Principe |
|---|---|---|
| **Accès par tenant** | Sécurité | Chaque organisation cliente ne voit que ses lignes. Tenant déduit du jeton d'authentification (jamais du LLM), colonne déclarée dans le Domain Pack (`tenantColumn`), règle Java `TenantScopeRule`, et sécurité par ligne PostgreSQL comme garantie finale |
| **Prévision** | Composant statistique ou ML séparé | Les prévisions sont écrites dans des tables dédiées que l'assistant interroge en `SELECT` |
| **Alertes** | Planificateur et notification | Job planifié sur des seuils exprimés à partir des KPIs du dictionnaire |

**Hors périmètre :** toute écriture ou action sur les données métier (contraire au principe de lecture seule).

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
├── data-warehouse/        Jeux de données locaux (ignorés par Git)
└── docs/                  Documents de cadrage
```

## Stack

| Couche | Technologie | État |
|---|---|---|
| Backend | Java 21, Quarkus (REST, Jackson, OpenAPI, Agroal, config YAML) | En place |
| Base de données | PostgreSQL (driver JDBC Quarkus) | Décidé, configuration à migrer depuis DuckDB (Palier 1) |
| Analyse SQL | JSqlParser 5.0 | Dépendance ajoutée |
| Lecture du Domain Pack | Jackson YAML | Fonctionnel |
| IA | Quarkus LangChain4j, RAG (pgvector), Guardrails | Prévu (Palier 2) |
| Streaming et Medallion | Redpanda, dbt-postgres | Prévu (Palier 3) |
| Frontend | Angular | Prévu (Palier 4) |
| Déploiement | Docker, Kubernetes (K3d), AWS | Prévu (Palier 5) |
| Observabilité | OpenTelemetry, Micrometer, Prometheus, Grafana | Prévu (Palier 6) |

## Démarrage

**Prérequis :** JDK 21, Maven (ou le wrapper `mvnw` fourni), Docker (pour PostgreSQL, à venir).

```bash
git clone https://github.com/El-Hadi21/datapulse-platform.git
cd datapulse-platform/engine-quarkus
./mvnw quarkus:dev        # Windows : mvnw.cmd quarkus:dev
```

Par défaut :

- l'API écoute sur `http://localhost:8080` ;
- le Domain Pack est lu depuis `../domain-packs/olist-ecommerce/semantic-dictionary.yaml` ;
- la connexion à PostgreSQL (hôte, base, rôle en lecture seule, mot de passe) sera fournie par variables d'environnement. Le `docker-compose.yml` avec PostgreSQL et le chargement du dataset Olist (à télécharger depuis Kaggle) font partie du Palier 1 et ne sont pas encore documentés ici.

Les chemins étant relatifs, la commande doit être lancée depuis `engine-quarkus/`.

## Avancement

Le développement avance par paliers : on ne passe au suivant que lorsque le précédent est compris et validé.

**Palier 1 : moteur backend et exécution SQL sécurisée** (en cours)

- [x] Projet Quarkus initialisé (Java 21)
- [x] Modèle du Domain Pack en records Java (`DomainPack`, `TableMetadata`, `ColumnMetadata`, `SemanticMetric`)
- [x] `DomainPackLoader` : lecture du dictionnaire sémantique YAML au démarrage
- [x] Dictionnaire sémantique Olist : table `fact_orders`, KPIs GMV et AOV
- [x] `SqlSecurityRule` et `SelectOnlyRule` (liste blanche : seules les instructions `SELECT` sont acceptées)
- [x] `LimitInjectionRule` : limite de lignes configurable (`datapulse.security.max-limit`, 100 par défaut). Cas à couvrir : `UNION` et limites non littérales
- [x] Migration de la configuration vers PostgreSQL et `docker-compose.yml`
- [ ] Chargement du dataset Olist dans PostgreSQL et construction de `fact_orders`
- [ ] `AllowedTablesRule` (y compris fonctions de table, sous-requêtes et CTE) et `SqlValidationPipeline` (une seule instruction autorisée, exécution de la requête réécrite)
- [ ] `ReadOnlySqlExecutor` : exécution JDBC en lecture seule, avec délai maximal
- [ ] Endpoint `POST /api/query/execute` (`QueryResource` à implémenter)
- [ ] Tests unitaires JUnit 5

**Paliers suivants**

- [ ] **Palier 2** : agent NL2SQL avec Quarkus LangChain4j, RAG sur le dictionnaire (pgvector), auto-correction par guardrail (3 essais max), benchmark de fiabilité
- [ ] **Palier 3** : rejeu d'événements via Redpanda, couches Bronze/Silver/Gold avec dbt, second Domain Pack pour prouver l'agnosticisme
- [ ] **Palier 4** : interface web (sélecteur de domaine, chat, requête SQL affichée, résultats)
- [ ] **Palier 5** : Kubernetes (K3d), sondes de santé, correspondance AWS
- [ ] **Palier 6** : télémétrie, métriques, tableaux de bord
- [ ] **Palier 7 (hors MVP)** : accès par tenant, prévision, alertes

## Évaluation prévue

Chaque Domain Pack embarquera un jeu de questions de référence (`benchmark.json`). L'agent sera comparé **avant / après** activation du RAG et des guardrails, avec les métriques suivantes : validité SQL, exactitude du résultat, taux de colonnes inventées, taux de refus pertinent, latence médiane. Les résultats seront publiés ici.

## Documentation

- [Document de cadrage v3.1](docs/) : vision, principes, décision PostgreSQL, périmètre, pistes d'extension et roadmap détaillée.

## Licence

À définir.