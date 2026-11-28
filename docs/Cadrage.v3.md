# Document de Cadrage v3.1 — Moteur Analytique & Agent NL2SQL Générique

> **Changements par rapport à la v3**
> 1. **Moteur de données : PostgreSQL** remplace DuckDB (voir section 5, décision d'architecture).
> 2. **Périmètre fonctionnel explicité** et **pistes d'extension** documentées (section 6) : accès par tenant, prévision, alertes.
> 3. Nouvelle règle de sécurité prévue : `TenantScopeRule`, et champ optionnel `tenantColumn` dans le Domain Pack.
> 4. Roadmap mise à jour (Palier 1, Palier 3) et nouveau Palier 7 (extensions, hors MVP).
> 5. Résidus de copier-coller retirés (mot « Markdown » en tête, marqueurs `[cite: 13]`, `$\rightarrow$`) et numérotation des sections corrigée.

---

## 1. Vision du Projet

Construire une plateforme analytique temps réel et un moteur d'intelligence artificielle **NL2SQL (Natural Language to SQL)** de niveau industriel, **100 % agnostique du domaine métier**.

En branchant simplement un nouveau "Domain Pack" (schéma de données + dictionnaire sémantique YAML) et une source de données, la plateforme expose automatiquement des KPIs fiables et un assistant IA capable d'interroger l'entrepôt de données en langage naturel **sans jamais inventer un chiffre**.

---

## 2. Principes d'Ingénierie

1. **Zéro Hallucination Numérique :** Le LLM n'effectue aucun calcul. Il consulte la couche sémantique (RAG), génère une requête SQL, passe par un pipeline de validation syntaxique strict en Java, exécute la requête en lecture seule et synthétise le résultat.
2. **Agnosticisme Métier (Plugin Architecture) :** Aucune table ni formule métier n'est codée en dur en Java. Le moteur lit dynamiquement un contrat de domaine (`domain-pack.yaml`).
3. **Architecture Full-Java Enterprise (Quarkus + LangChain4j) :** Unification du backend, du streaming temps réel et de l'orchestration IA au sein d'un écosystème Quarkus typé, compilé et économe en ressources.
4. **Cloud-Native Ready (K8s / AWS Target) :** Conception orientée conteneurs et déploiement déclaratif sur Kubernetes (K3s/EKS).
5. **Lecture seule, sans exception :** Le moteur n'écrit jamais dans les données métier. Toute fonctionnalité qui nécessiterait des écritures est hors périmètre (section 6.3).
6. **La sécurité ne dépend jamais du LLM :** Les contrôles (type d'instruction, tables, limites, périmètre d'accès) sont appliqués en Java et en base, sur la requête validée et réécrite, jamais sur la chaîne reçue.

---

## 3. Architecture Technique Globale

```text
[1. INGESTION & STREAMING TEMPS RÉEL]
Dataset Kaggle (CSV Olist)
──► Simulateur de Rejeu (Quarkus Reactive Messaging)
──► Redpanda (Compatible Kafka, ultra-léger en RAM)

[2. ARCHITECTURE MEDALLION (DATA LAKEHOUSE LOCAL)]
Redpanda / Fichiers Bruts
──► Couche Bronze (Données brutes)
──► Couche Silver (Nettoyage, dédoublonnage et typage)
──► Couche Gold (dbt + PostgreSQL : Modèles analytiques en étoile & Vues KPIs)

[3. CŒUR DU MOTEUR : BACKEND JAVA 21 & QUARKUS]
┌─────────────────────────────────────────────────────────────────────────┐
│ API REST & SSE (Quarkus REST)                                           │
│   │                                                                     │
│   ├─► Module Domain Loader : Charge le "Domain Pack" (YAML + RAG Docs)  │
│   │                                                                     │
│   ├─► Orchestrateur IA (Quarkus LangChain4j) :                          │
│   │     1. Classification d'intention & Injection RAG (Schéma + KPIs)   │
│   │     2. Génération SQL structurée                                    │
│   │     3. Boucle d'auto-correction (via @Tool / Guardrails)            │
│   │                                                                     │
│   ├─► Pipeline de Sécurité SQL (JSqlParser - Chain of Responsibility) : │
│   │     [SelectOnlyRule] -> [AllowedTablesRule] -> [TenantScopeRule*]   │
│   │     -> [LimitInjectionRule]          (* piste d'extension, 6.1)     │
│   │                                                                     │
│   └─► Exécuteur Analytique JDBC (rôle PostgreSQL en lecture seule,      │
│       schéma Gold uniquement)                                           │
└─────────────────────────────────────────────────────────────────────────┘
▲
│ (HTTP / JSON)

[4. FRONTEND ANALYTIQUE (Développé après validation du moteur)]
Interface Web Moderne (Sélecteur de Domaine, Chat NL2SQL avec affichage de la
requête SQL générée, Tableaux de données dynamiques et Graphiques).

[5. INFRASTRUCTURE & CLOUD]
Conteneurisation Docker ──► Orchestration Kubernetes (K3d local / Architecture AWS EKS)
```

---

## 4. Le Concept de "Domain Pack" (Comment rendre le projet générique)

Pour passer du E-Commerce (Dataset Kaggle Olist) à l'Hôtellerie sans toucher une seule ligne de code Java, chaque domaine est défini par un dossier contenant :

1. **`semantic-dictionary.yaml`** :
   * Liste des tables Gold autorisées et description de chaque colonne.
   * Catalogue des KPIs officiels (Nom, Synonymes, Formule SQL exacte, Règles d'exclusion comme les commandes annulées).
   * *(Piste d'extension, 6.1)* Champ optionnel **`tenantColumn`** par table : nom de la colonne qui rattache chaque ligne à un tenant (ex. `seller_id`, `hotel_id`). Une table sans ce champ est considérée comme partagée par tous.
2. **`business-rules.md`** : Document vectorisé automatiquement au démarrage dans le store d'embeddings (RAG) pour donner le contexte métier au LLM.
3. **`benchmark.json`** : Jeu de questions/réponses SQL de référence propres à ce domaine pour tester la fiabilité de l'agent.

---

## 5. Décision d'architecture : PostgreSQL comme moteur de données

**Statut :** décidé (v3.1). Remplace le choix DuckDB de la v3.

### Contexte

Le projet a besoin d'un moteur SQL pour la couche Gold, interrogé en lecture seule par le backend. Le jeu de données Olist (environ 100 000 commandes) est petit : DuckDB et PostgreSQL y répondent tous deux en un temps négligeable. Le choix ne se joue donc pas sur la performance, mais sur l'évolutivité et les compétences démontrées.

### Décision

Utiliser **PostgreSQL** pour toutes les couches (Bronze, Silver, Gold), avec :

* un **rôle SQL en lecture seule**, limité au schéma Gold, pour l'exécuteur du moteur ;
* **`dbt-postgres`** pour les transformations ;
* **`pgvector`** comme store d'embeddings pour le RAG (Palier 2) ;
* la **sécurité par ligne (Row Level Security)** disponible pour la piste multi-tenant (6.1).

### Pourquoi

| Critère | Raison |
|---|---|
| Accès par tenant | PostgreSQL offre un mécanisme natif de sécurité par ligne, qui garantit l'isolation même si une règle Java est contournée |
| Concurrence | Plusieurs écrivains et plusieurs utilisateurs : indispensable dès que le pipeline temps réel (Redpanda) alimente la base pendant que l'API la lit |
| RAG | `pgvector` évite d'ajouter un composant vectoriel séparé |
| Cloud | Chemin direct vers AWS (RDS) et plusieurs réplicas de l'API en Kubernetes, sans partager un fichier |
| Compétence | Standard en entreprise, bien plus attendu qu'un moteur embarqué sur un profil Backend Java |
| Besoins non agrégés | Le moteur gère très bien les requêtes d'agrégation à cette échelle ; l'orientation en lignes n'est pas un obstacle pour Olist |

### Ce que cela coûte

* Un **service à lancer** (conteneur Docker) au lieu d'un simple fichier : le Palier 1 inclut donc un `docker-compose.yml` minimal avec PostgreSQL.
* Un **rôle en lecture seule et des schémas** à configurer (Bronze, Silver, Gold).
* Les **chargements** passent par `COPY` au lieu de lire directement les CSV.

### Alternative écartée : DuckDB

DuckDB reste excellent pour l'analytique locale sans infrastructure, mais un seul processus peut y écrire, et il n'offre pas de sécurité par ligne native ni de montée en charge horizontale. Il pourrait rester un **outil de prototypage** local, sans être le moteur du projet.

### Impact sur le code existant

* `pom.xml` : remplacer `duckdb_jdbc` par `quarkus-jdbc-postgresql`.
* `application.yml` : `db-kind: postgresql`, URL JDBC PostgreSQL, rôle et mot de passe en variables d'environnement.
* Écrire du **SQL portable** (pas de fonctions spécifiques comme `read_csv_auto` dans le code Java), ce qui garde le moteur indépendant du SGBD.

---

## 6. Périmètre fonctionnel et pistes d'extension

### 6.0 Dans le périmètre (MVP)

Un assistant de **questions-réponses analytiques en lecture seule** : KPIs, agrégations, comparaisons dans le temps, listes filtrées, tops, détails d'un enregistrement, le tout par requêtes `SELECT` validées et mesurées par un benchmark.

### 6.1 Piste d'extension A : accès par tenant (multi-tenant)

**Besoin.** Quand plusieurs organisations utilisent la plateforme (par exemple chacune responsable de son propre périmètre de données), chacune ne doit voir que **ses** lignes. La liste blanche de tables contrôle quelles tables sont lisibles, pas quelles **lignes**.

**Terminologie.** Le document utilise **« tenant »** (organisation cliente, périmètre d'accès) : un terme neutre qui convient à n'importe quel domaine, au lieu de termes liés à un métier (hôtel, vendeur, boutique).

**Conception envisagée :**

* Le tenant est **déduit de l'authentification** (jeton), jamais de la question ni du LLM.
* Le Domain Pack déclare quelle colonne porte le tenant (`tenantColumn`).
* **Défense en profondeur, deux couches :**
   1. une règle Java `TenantScopeRule` qui ajoute le filtre sur le tenant à chaque accès aux tables concernées ;
   2. la **sécurité par ligne de PostgreSQL**, qui reste la garantie finale, car la réécriture Java doit couvrir sous-requêtes, CTE et `UNION`, et reste donc plus fragile.
* Démonstration possible sur Olist : les **vendeurs** comme tenants (`seller_id` est dans `order_items`, avec une commande pouvant impliquer plusieurs vendeurs : le grain des tables Gold est à choisir avec soin).

**Points d'attention :** le tenant est transmis à la base **dans la transaction** (avec un pool de connexions, une variable de session persistante risque de fuiter d'une requête à l'autre) ; les données personnelles (RGPD) sont à traiter séparément.

### 6.2 Pistes d'extension : besoins qui demandent un autre composant (pas du NL2SQL)

Ces besoins ne s'implémentent pas en générant du SQL. Ils s'ajoutent comme **composants séparés** qui lisent la couche Gold et, au besoin, écrivent leurs résultats dans des **tables dédiées** que le moteur NL2SQL peut ensuite interroger en lecture seule.

| Piste | Nature | Principe d'intégration |
|---|---|---|
| **B. Prévision** (rythme de ventes ou de réservations, volume futur, tarification dynamique) | Modèle statistique ou ML | Un composant calcule des prévisions et les stocke dans une table Gold (`forecast_*`) déclarée dans le Domain Pack ; l'assistant répond ensuite à « quelle prévision pour le mois prochain ? » par un simple `SELECT` |
| **C. Alertes** (« préviens-moi si un KPI passe sous un seuil ») | Planificateur et notification | Un job planifié (Quarkus Scheduler) évalue des seuils exprimés à partir des KPIs officiels du dictionnaire et envoie une notification |

### 6.3 Hors périmètre

* **Actions et écritures** (modifier un tarif, annuler une commande, fermer un stock) : elles contredisent le principe de lecture seule et exigeraient un modèle de sécurité différent (confirmation humaine, droits, audit).
* **Non retenus pour l'instant :** benchmark concurrentiel (données externes indisponibles) et analyse d'avis en texte libre (traitement de langage, pas du SQL). *À reconsidérer plus tard si le besoin apparaît.*

---

## 7. Roadmap d'Apprentissage & Réalisation ("Learning by Doing" — Sans limite de temps)

Cette feuille de route est organisée par Paliers de Maîtrise. On ne passe au palier suivant que lorsque le palier actuel est compris en profondeur et validé techniquement.

---

### PALIER 1 : Le Cœur Backend Quarkus & Le Moteur d'Exécution SQL Sécurisé

Ici, pas encore d'IA ni de Kafka. On construit une forteresse Java orientée objet capable de charger un domaine et d'exécuter du SQL en toute sécurité.

**Ce que tu apprends (Docs & Concepts) :**

* Finir les guides officiels Quarkus (CDI / Injection de dépendances, Configuration @ConfigMapping, gestion des profils).
* Révision approfondie de la POO Java 21 (Records, Interfaces scellées, Design Patterns Chain of Responsibility, Strategy et Factory).
* PostgreSQL avec JDBC (rôles, schémas, droits en lecture seule) et JSqlParser (analyse d'arbre syntaxique SQL en Java).

**Ce que tu construis :**

* Un `docker-compose.yml` minimal avec PostgreSQL, et un rôle en lecture seule limité au schéma Gold.
* Téléchargement du dataset Kaggle Olist et chargement initial dans PostgreSQL (tables brutes puis `fact_orders`).
* Le chargeur de DomainPack dans Quarkus (lecture d'un fichier YAML décrivant les tables et KPIs d'Olist).
* La chaîne de validation SQL en Java (SqlValidationPipeline) : si on lui passe un DROP TABLE, une table qui n'est pas dans le DomainPack, ou plusieurs instructions à la suite, elle lève une exception métier propre.
* Un endpoint REST /api/query/execute couvert par des tests unitaires JUnit 5, qui exécute la requête **validée et réécrite**, jamais la chaîne brute.

---

### PALIER 2 : L'Agent IA NL2SQL & Le RAG Sémantique (Quarkus + LangChain4j)

On branche le cerveau IA directement dans Quarkus en suivant ton livre "Applied AI for Java Development".

**Ce que tu apprends (Docs & Concepts) :**

* Lecture active de Applied AI for Java Development + Documentation officielle de quarkus-langchain4j.
* Concepts clés : @RegisterAiService, @SystemMessage, @UserMessage, Structured Outputs, @Tool (Function Calling), InputGuardrail / OutputGuardrail et RAG (Embeddings, Vector Store **pgvector**, ContentRetriever).

**Ce que tu construis :**

* Le RAG sur le dictionnaire de données : Au démarrage de Quarkus, le fichier semantic-dictionary.yaml est découpé et indexé pour que le LLM ne reçoive que les définitions des tables et KPIs liés à la question posée.
* La boucle d'auto-correction via OutputGuardrail : C'est la fonctionnalité tueuse de Quarkus LangChain4j ! Tu crées un SqlExecutionGuardrail qui intercepte le SQL généré par le LLM, le passe à ton validateur du Palier 1, et exécute la requête. Si le SQL contient une colonne inventée ou une erreur de syntaxe, le Guardrail renvoie automatiquement l'erreur SQL au LLM (reprompt) pour qu'il se corrige tout seul (jusqu'à 3 essais max) avant de répondre à l'utilisateur !
* Le Benchmark de fiabilité : Un script de test qui lance une trentaine de questions sur Olist et mesure le taux de réussite avant et après l'activation du RAG et des Guardrails.

---

### PALIER 3 : Le Pipeline Data Engineering (Streaming Redpanda, Medallion & dbt)

Maintenant que le moteur NL2SQL fonctionne, on construit la vraie usine à données en amont.

**Ce que tu apprends (Docs & Concepts) :**

* Architecture Event-Driven (Kafka / Redpanda) : Comprendre les Topics, Partitions, Producers, Consumers et Offsets. (On utilise Redpanda sous Docker : 100 % compatible avec le protocole Kafka, mais écrit en C++ sans JVM, donc très léger en RAM).
* Architecture Medallion (Bronze / Silver / Gold) & dbt Core (**dbt-postgres**) : Modélisation dimensionnelle (tables de faits et de dimensions), tests de qualité de données (unique, not_null, accepted_values) et lignage (Lineage).

**Ce que tu construis :**

* Le Simulateur de Rejeu Temps Réel : Un service Quarkus (quarkus-messaging-kafka) qui lit les lignes historiques du dataset Kaggle Olist et les publie comme de vrais événements de commandes en temps réel dans un topic Redpanda (orders-events).
* Le Consommateur Bronze : Un lecteur qui ingère ces événements dans la couche Bronze.
* Le Projet dbt (Silver → Gold) : Les modèles SQL dbt qui nettoient les données brutes et construisent les tables Gold propres sur lesquelles ton agent du Palier 2 vient se brancher. Il remplace le script SQL du Palier 1.
* Test d'agnosticisme : Tu crées un 2ème mini "Domain Pack" (ex: sur les données hôtelières ou un autre CSV Kaggle) pour prouver que ton moteur Quarkus change de métier par simple configuration.

---

### PALIER 4 : L'Interface Utilisateur (Frontend Analytique)

Le moteur et le pipeline data sont solides : on leur donne un visage visuel propre.

**Ce que tu apprends & construis :**

* Une interface web dédiée (par exemple en Angular, que tu maîtrises déjà, connectée à ton API Quarkus).

**Fonctionnalités clés :**

* Un sélecteur de "Domain Pack" en haut de l'écran (pour démontrer l'aspect générique en direct).
* Une interface conversationnelle où l'utilisateur pose sa question métier.
* Un panneau de transparence ("Explainability") qui affiche la requête SQL exacte exécutée, les tables utilisées, le temps d'exécution et le tableau de résultats (ou graphique).

---

### PALIER 5 : Cloud-Native, Kubernetes (K8s) & Orientation AWS

On passe du poste de développement local à une architecture déployable dans le Cloud.

**Ce que tu apprends (Docs & Concepts) :**

* Les fondamentaux de Kubernetes : Pod, Deployment, Service (ClusterIP / NodePort), Ingress, ConfigMap, Secret, PersistentVolumeClaim (PVC), et les sondes de santé (Liveness / Readiness probes avec quarkus-smallrye-health).
* La correspondance directe entre ton cluster local K3d et AWS (EKS, ECR, S3, RDS pour PostgreSQL).

**Ce que tu construis :**

* Ajout de quarkus-kubernetes et quarkus-smallrye-health à ton projet Java.
* Déploiement de ton API Quarkus, de PostgreSQL et de ton Frontend dans un cluster Kubernetes local (K3d), avec montée en charge (ex: 2 réplicas de ton pod Quarkus) et vérification de la résilience.

---

### PALIER 6 (Priorité Basse / Bonus) : Observabilité, Télémétrie IA & Monitoring

La cerise sur le gâteau une fois que tout le reste tourne.

**Ce que tu construis :**

* Activation d'OpenTelemetry et Micrometer / Prometheus dans Quarkus (il suffit d'ajouter l'extension Quarkus dédiée pour tracer automatiquement les appels HTTP, les requêtes SQL et les appels LLM de LangChain4j).
* Un dashboard Grafana (ou Langfuse) pour visualiser la latence des requêtes, le nombre de tokens consommés et le taux de déclenchement des Guardrails d'auto-correction SQL.

---

### PALIER 7 (Hors MVP, non engagé) : Extensions

Ce palier regroupe les pistes de la section 6. Il n'est engagé que si le MVP est terminé et mesuré.

* **7.1 Accès par tenant** : champ `tenantColumn` dans le Domain Pack, authentification (jeton avec identifiant de tenant), `TenantScopeRule`, sécurité par ligne PostgreSQL, tests d'isolation (un tenant ne doit jamais voir les lignes d'un autre).
* **7.2 Prévision** : composant statistique séparé qui écrit dans des tables `forecast_*` interrogeables par le moteur.
* **7.3 Alertes** : job planifié sur des seuils de KPIs et notification.