node {
    env.JAVA_HOME="${tool 'jdk-oracle-8'}"
    env.PATH="${env.JAVA_HOME}/bin:${env.PATH}"
    try {
        stage('Clone') {
            checkout scm
        }

        withMaven(maven: 'M3', jdk: 'jdk-oracle-8', options: [artifactsPublisher(disabled: true)] ) {
            stage('Typecheck') {
                sh "mvn clean compile"
            }

            stage('Test') {
                sh "mvn test"
            }

            stage('Packaging') {
                sh "mvn package"
            }

            stage('Deploy') {
                if (env.BRANCH_NAME == "master") {
                    sh "mvn -DskipTests deploy " 
                    sh "mvn -DskipTests install" 
                }
            }
        }


        if (currentBuild.previousBuild.result == "FAILURE") { 
            slackSend (color: '#5cb85c', message: "BUILD BACK TO NORMAL: <${env.BUILD_URL}|${env.JOB_NAME} [${env.BUILD_NUMBER}]>")
        }

        build job: '../rascal-eclipse-libraries/master', wait: false
        build job: '../rascal-core-plugin/master', wait: false
    } catch (e) {
        slackSend (color: '#d9534f', message: "FAILED: <${env.BUILD_URL}|${env.JOB_NAME} [${env.BUILD_NUMBER}]>")
            throw e
    }
}
