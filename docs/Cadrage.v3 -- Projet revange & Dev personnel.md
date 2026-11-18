Document de Cadrage v3 (Architecture 100 % Quarkus, Générique & Cloud-Native) Markdown

# Document de Cadrage — Moteur Analytique & Agent NL2SQL Générique

## 1. Vision du Projet

Construire une plateforme analytique temps réel et un moteur d'intelligence artificielle **NL2SQL (Natural Language to SQL)** de niveau industriel, **100 % agnostique du domaine métier**.

En branchant simplement un nouveau "Domain Pack" (schéma de données + dictionnaire sémantique YAML) et une source de données, la plateforme expose automatiquement des KPIs fiables et un assistant IA capable d'interroger l'entrepôt de données en langage naturel **sans jamais inventer un chiffre**.

---

## 2. Principes d'Ingénierie

1. **Zéro Hallucination Numérique :** Le LLM n'effectue aucun calcul. Il consulte la couche sémantique (RAG), génère une requête SQL, passe par un pipeline de validation syntaxique strict en Java, exécute la requête en lecture seule et synthétise le résultat.
2. **Agnosticisme Métier (Plugin Architecture) :** Aucune table ni formule métier n'est codée en dur en Java. Le moteur lit dynamiquement un contrat de domaine (`domain-pack.yaml`).
3. **Architecture Full-Java Enterprise (Quarkus + LangChain4j) :** Unification du backend, du streaming temps réel et de l'orchestration IA au sein d'un écosystème Quarkus typé, compilé et économe en ressources.
4. **Cloud-Native Ready (K8s / AWS Target) :** Conception orientée conteneurs et déploiement déclaratif sur Kubernetes (K3s/EKS).

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
──► Couche Gold (dbt + DuckDB : Modèles analytiques en étoile & Vues KPIs)

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
│   │     [SelectOnlyRule] -> [AllowedTablesRule] -> [LimitInjectionRule] │
│   │                                                                     │
│   └─► Exécuteur Analytique JDBC (Read-Only sur DuckDB Gold)             │
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
2. **`business-rules.md`** : Document vectorisé automatiquement au démarrage dans le store d'embeddings (RAG) pour donner le contexte métier au LLM.
3. **`benchmark.json`** : Jeu de questions/réponses SQL de référence propres à ce domaine pour tester la fiabilité de l'agent.

---

## 3. Roadmap d'Apprentissage & Réalisation ("Learning by Doing" — Sans limite de temps)

Cette feuille de route est organisée par Paliers de Maîtrise. On ne passe au palier suivant que lorsque le palier actuel est compris en profondeur et validé techniquement.

---

### PALIER 1 : Le Cœur Backend Quarkus & Le Moteur d'Exécution SQL Sécurisé

Ici, pas encore d'IA ni de Kafka. On construit une forteresse Java orientée objet capable de charger un domaine et d'exécuter du SQL en toute sécurité.

**Ce que tu apprends (Docs & Concepts) :**

* Finir les guides officiels Quarkus (CDI / Injection de dépendances, Configuration @ConfigMapping, gestion des profils).
* Révision approfondie de la POO Java 21 (Records, Interfaces scellées, Design Patterns Chain of Responsibility, Strategy et Factory).
* Découverte de DuckDB (le moteur SQL analytique en fichier local ultra-rapide) et de JSqlParser (analyse d'arbre syntaxique SQL en Java).

**Ce que tu construis :**

* Téléchargement du dataset Kaggle Olist et chargement initial dans un fichier DuckDB.
* Le chargeur de DomainPack dans Quarkus (lecture d'un fichier YAML décrivant les tables et KPIs d'Olist).
* La chaîne de validation SQL en Java (SqlValidationPipeline) : si on lui passe un DROP TABLE ou une table qui n'est pas dans le DomainPack, elle lève une exception métier propre.
* Un endpoint REST /api/query/execute couvert par des tests unitaires JUnit 5.

---

### PALIER 2 : L'Agent IA NL2SQL & Le RAG Sémantique (Quarkus + LangChain4j)

On branche le cerveau IA directement dans Quarkus en suivant ton livre "Applied AI for Java Development".

**Ce que tu apprends (Docs & Concepts) :**

* Lecture active de Applied AI for Java Development + Documentation officielle de quarkus-langchain4j.
* Concepts clés : @RegisterAiService, @SystemMessage, @UserMessage, Structured Outputs, @Tool (Function Calling), InputGuardrail / OutputGuardrail et RAG (Embeddings, Vector Store en mémoire ou pgvector, ContentRetriever).

**Ce que tu construis :**

* Le RAG sur le dictionnaire de données : Au démarrage de Quarkus, le fichier semantic-dictionary.yaml est découpé et indexé pour que le LLM ne reçoive que les définitions des tables et KPIs liés à la question posée.
* La boucle d'auto-correction via OutputGuardrail : C'est la fonctionnalité tueuse de Quarkus LangChain4j ! Tu crées un SqlExecutionGuardrail qui intercepte le SQL généré par le LLM, le passe à ton validateur du Palier 1, et exécute la requête. Si le SQL contient une colonne inventée ou une erreur de syntaxe, le Guardrail renvoie automatiquement l'erreur SQL au LLM (reprompt) pour qu'il se corrige tout seul (jusqu'à 3 essais max) avant de répondre à l'utilisateur !
* Le Benchmark de fiabilité : Un script de test qui lance une trentaine de questions sur Olist et mesure le taux de réussite avant et après l'activation du RAG et des Guardrails.

---

### PALIER 3 : Le Pipeline Data Engineering (Streaming Redpanda, Medallion & dbt)

Maintenant que le moteur NL2SQL fonctionne, on construit la vraie usine à données en amont.

**Ce que tu apprends (Docs & Concepts) :**

* Architecture Event-Driven (Kafka / Redpanda) : Comprendre les Topics, Partitions, Producers, Consumers et Offsets. (On utilise Redpanda sous Docker : 100 % compatible avec le protocole Kafka, mais écrit en C++ sans JVM, donc très léger en RAM).
* Architecture Medallion (Bronze / Silver / Gold) & dbt Core (dbt-duckdb) : Modélisation dimensionnelle (tables de faits et de dimensions), tests de qualité de données (unique, not_null, accepted_values) et lignage (Lineage).

**Ce que tu construis :**

* Le Simulateur de Rejeu Temps Réel : Un service Quarkus (quarkus-messaging-kafka) qui lit les lignes historiques du dataset Kaggle Olist et les publie comme de vrais événements de commandes en temps réel dans un topic Redpanda (orders-events).
* Le Consommateur Bronze : Un lecteur qui ingère ces événements dans la couche Bronze.
* Le Projet dbt (Silver $\rightarrow$ Gold) : Les modèles SQL dbt qui nettoient les données brutes et construisent les tables Gold propres sur lesquelles ton agent du Palier 2 vient se brancher[cite: 13].
* Test d'agnosticisme : Tu crées un 2ème mini "Domain Pack" (ex: sur les données hôtelières ou un autre CSV Kaggle) pour prouver que ton moteur Quarkus change de métier par simple configuration.

---

### PALIER 4 : L'Interface Utilisateur (Frontend Analytique)

Le moteur et le pipeline data sont solides : on leur donne un visage visuel propre.

**Ce que tu apprends & construis :**

* Une interface web dédiée (par exemple en Angular, que tu maîtrises déjà, connectée à ton API Quarkus).

**Fonctionnalités clés :**

* Un sélecteur de "Domain Pack" en haut de l'écran (pour démontrer l'aspect générique en direct).
* Une interface conversationnelle où l'utilisateur pose sa question métier.
* Un panneau de transparence ("Explainability") qui affiche la requête SQL exacte exécutée, les tables utilisées, le temps d'exécution et le tableau de résultats (ou graphique)[cite: 13].

---

### PALIER 5 : Cloud-Native, Kubernetes (K8s) & Orientation AWS

On passe du poste de développement local à une architecture déployable dans le Cloud.

**Ce que tu apprends (Docs & Concepts) :**

* Les fondamentaux de Kubernetes : Pod, Deployment, Service (ClusterIP / NodePort), Ingress, ConfigMap, Secret, PersistentVolumeClaim (PVC), et les sondes de santé (Liveness / Readiness probes avec quarkus-smallrye-health).
* La correspondance directe entre ton cluster local K3d et AWS (EKS, ECR, S3, RDS).

**Ce que tu construis :**

* Ajout de quarkus-kubernetes et quarkus-smallrye-health à ton projet Java.
* Déploiement de ton API Quarkus et de ton Frontend dans un cluster Kubernetes local (K3d), avec montée en charge (ex: 2 réplicas de ton pod Quarkus) et vérification de la résilience.

---

### PALIER 6 (Priorité Basse / Bonus) : Observabilité, Télémétrie IA & Monitoring

La cerise sur le gâteau une fois que tout le reste tourne.

**Ce que tu construis :**

* Activation d'OpenTelemetry et Micrometer / Prometheus dans Quarkus (il suffit d'ajouter l'extension Quarkus dédiée pour tracer automatiquement les appels HTTP, les requêtes SQL et les appels LLM de LangChain4j)[cite: 13].
* Un dashboard Grafana (ou Langfuse) pour visualiser la latence des requêtes, le nombre de tokens consommés et le taux de déclenchement des Guardrails d'auto-correction SQL[cite: 13].
