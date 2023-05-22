#!/usr/bin/env groovy
@Library('clb-jenkins-library') _

def buildType() {
	if ("release/4.6.xclb".equals(env.BRANCH_NAME))
		return 'ci'
	else
		return 'release'
}

pipeline {
    agent { label '2019-buildserver1' }

	options {
		timestamps()
		ansiColor('xterm')
		buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '10'))
	}

    stages {
		stage('Administration') {
			steps {
				step([$class: 'LastChangesPublisher', since: 'PREVIOUS_REVISION'])
			}
		}

		stage('Build') {
			steps {
				withCredentials([usernamePassword(credentialsId: 'sign_android', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD'),
								file(credentialsId: 'releasefile_old', variable: 'KEYSTORE_VAR'),
								file(credentialsId: 'upload_jks', variable: 'KEYSTORE_UPLOAD')]) {						
					bat "gradlew assembleRelease -PkeyPassword=${PASSWORD} -PstorePassword=${PASSWORD} -PkeyAlias=${USERNAME} -PstoreFile=${KEYSTORE_VAR}"
					bat "gradlew bundleRelease -PkeyPassword=${PASSWORD} -PstorePassword=${PASSWORD} -PkeyAlias=${USERNAME} -PstoreFile=${KEYSTORE_UPLOAD}"
				}
			}
        }		
		stage('Post build actions') {
			parallel {				
				stage('Store artifacts') {
					steps {
						archiveArtifacts(artifacts: 'app/build/outputs/apk/linphone/release/*.*,app/build/outputs/bundle/clbRelease/*.*,app/build/outputs/bundle/clbTypeMRelease/*.*,app/build/outputs/bundle/clbConfigRelease/*.*')
					}
				}
			}
		}
    }
	post {
		fixed {
			emailext(subject: "${currentBuild.currentResult}: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
					 body: """<${env.BUILD_URL}>""",
					 recipientProviders: [developers(), requestor()])
		}
		unsuccessful {
			emailext(subject: "${currentBuild.currentResult}: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
					 body: """<${env.BUILD_URL}>\n\n\${BUILD_LOG, escapeHtml=true}""",
					 recipientProviders: [developers(), requestor()])
		}
    }
}