pipeline {
    // Exécute le pipeline directement dans le conteneur Jenkins (qui embarque JDK 21)
    agent any

    stages {
        stage('Build & Test (Quarkus Engine)') {
            steps {
                // On se place dans le sous-dossier engine-quarkus pour que le chemin relatif
                // ../domain-packs/olist-ecommerce/semantic-dictionary.yaml reste valide
                dir('engine-quarkus') {
                    // Nécessaire quand le projet est commité depuis Windows (droit d'exécution Linux)
                    sh 'chmod +x mvnw'
                    // -B (Batch mode) supprime les barres de progression pour des logs CI propres
                    sh './mvnw -B clean test'
                }
            }
        }
    }

    post {
        always {
            // Publie les résultats JUnit dans l'interface graphique de Jenkins
            junit allowEmptyResults: true, testResults: 'engine-quarkus/target/surefire-reports/*.xml'
        }
        success {
            echo 'Build et tests DataPulse validés.'
        }
        failure {
            echo 'Échec du pipeline : vérifie les logs Maven ou le rapport JUnit.'
        }
    }
}