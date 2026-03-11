#!groovy
library "knime-pipeline@$DEFAULT_LIBRARY_VERSION"

properties([
	pipelineTriggers([
        upstream("knime-shared/${env.BRANCH_NAME.replaceAll('/', '%2F')}")
    ]),
    parameters([p2Tools.getP2pruningParameter()]),
    buildDiscarder(logRotator(numToKeepStr: '5')),
    disableConcurrentBuilds()
])

try {
    // provide the name of the update site project
    knimetools.defaultTychoBuild('org.knime.update.hub.client')

    stage('Sonarqube analysis') {
        env.lastStage = env.STAGE_NAME
        workflowTests.runSonar()
    }
} catch (ex) {
    currentBuild.result = 'FAILURE'
    throw ex
} finally {
    notifications.notifyBuild(currentBuild.result);
}

/* vim: set shiftwidth=4 expandtab smarttab: */
