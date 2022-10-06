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
				withCredentials([certificate(credentialsId: 'releasekey_old', keystoreVariable: 'KEYSTORE_VAR', passwordVariable: 'PASSWORD_VAR'),
								certificate(credentialsId: 'uploadkey', keystoreVariable: 'KEYSTORE_UPLOAD', passwordVariable: 'PASSWORD_UPLOAD')]) {
					print 'KEYSTORE_VAR.collect { it }=' + KEYSTORE_VAR.collect { it }	
					print 'KEYSTORE_UPLOAD.collect { it }=' + KEYSTORE_UPLOAD.collect { it }								
					bat "gradlew assembleRelease -PkeyPassword=${PASSWORD_VAR} -PstorePassword=${PASSWORD_VAR} -PkeyAlias=clb -PstoreFile=${KEYSTORE_VAR}"
					bat "gradlew bundleRelease -PkeyPassword=${PASSWORD_UPLOAD} -PstorePassword=${PASSWORD_UPLOAD} -PkeyAlias=clb -PstoreFile=${KEYSTORE_UPLOAD}"
				}
			}
        }		
		stage('Post build actions') {
			parallel {				
				stage('Store artifacts') {
					steps {
						archiveArtifacts(artifacts: 'app/build/outputs/apk/linphone/release/*.*,app/build/outputs/bundle/clbRelease/*.*,app/build/outputs/bundle/clbConfigRelease/*.*')
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