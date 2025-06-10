def call(Map configMap) {
    pipeline {
        agent {
            node {
                label 'AGENT-1'
            }
        }

        environment {
            packageVersion = ''
        }

        options {
            timeout(time: 1, unit: 'HOURS')
            disableConcurrentBuilds()
        }

        parameters {
            booleanParam(name: 'Deploy', defaultValue: false, description: 'Toggle this value')
        }

        stages {
            stage('Get the version') {
                steps {
                    script {
                        def packageJson = readJSON file: 'package.json'
                        packageVersion = packageJson.version
                        echo "application version: $packageVersion"
                    }
                }
            }

            stage('Install dependencies') {
                steps {
                    sh 'npm install'
                }
            }

            stage('Unit tests') {
                steps {
                    sh 'echo "unit tests will run here"'
                }
            }

            stage('Sonar Scan') {
                steps {
                    sh '''
                        echo "usually command here is sonar-scanner"
                        echo "sonar scan will run here"
                    '''
                }
            }

            stage('Build') {
                steps {
                    sh """
                        ls -la
                        zip -q -r ${configMap.component}.zip ./* -x ".git" -x "*.zip"
                        ls -ltr
                    """
                }
            }

            stage('Publish Artifact') {
                steps {
                    nexusArtifactUploader(
                        nexusVersion: 'nexus3',
                        protocol: 'http',
                        nexusUrl: pipelineGlobals.nexusURL(),
                        groupId: 'com.roboshop',
                        version: "${packageVersion}",
                        repository: "${configMap.component}",
                        credentialsId: 'nexus-auth',
                        artifacts: [
                            [
                                artifactId: "${configMap.component}",
                                classifier: '',
                                file: "${configMap.component}.zip",
                                type: 'zip'
                            ]
                        ]
                    )
                }
            }

            stage('Deploy') {
                when {
                    expression {
                        return params.Deploy
                    }
                }
                steps {
                    script {
                        def buildParams = [
                            [$class: 'StringParameterValue', name: 'version', value: "${packageVersion}"],
                            [$class: 'StringParameterValue', name: 'environment', value: 'dev'],
                            [$class: 'BooleanParameterValue', name: 'Create', value: params.Deploy]
                        ]

                        build job: "../${configMap.component}-deploy", wait: true, parameters: buildParams
                    }
                }
            }
        }

        post {
            always {
                echo 'I will always say Hello again!'
                deleteDir()
            }
            failure {
                echo 'This runs when the pipeline fails. Generally used to send alerts.'
            }
            success {
                echo 'Pipeline succeeded!'
            }
        }
    }
}
