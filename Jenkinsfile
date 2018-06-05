pipeline {
	agent any

	options { buildDiscarder(logRotator(numToKeepStr: '10')) }
	
	tools { 
		maven 'maven-3' 
		jdk 'jdk-8' 
	}
	
	stages {
		stage ('Initialize') {
			steps {
				sh '''
					echo "PATH = ${PATH}"
					echo "M2_HOME = ${M2_HOME}"
					echo "JAVA_HOME = ${JAVA_HOME}"
					git clean -dfx
				''' 
			}
		}
		
		stage('build') {
			steps {
				sh 'mvn clean deploy -U --batch-mode'
			}
		}
	}

	post {
		success {
			junit allowEmptyResults: true, testResults:'target/surefire-reports/**/*.xml' 
		}

		failure {
			emailext (
				subject: "Jenkins Build failed: ${env.JOB_NAME} [${env.BUILD_NUMBER}]",
					// mimeType: 'text/html',
					body: """
						FAILED: ${env.JOB_NAME} [${env.BUILD_NUMBER}]:
						${env.BUILD_URL}
					""",
					recipientProviders: [[$class: 'DevelopersRecipientProvider']]
			)
		}
	}
}