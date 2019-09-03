#!/bin/bash

set -euo pipefail

CURRENT_VERSION=`mvn help:evaluate -Dtycho.mode=maven -Dexpression="project.version" | grep -v '^\[\|Download\w\+\:'`
RELEASE_VERSION=`echo $CURRENT_VERSION | sed "s/-.*//g"`

NEW_VERSION="$RELEASE_VERSION.$BUILD_ID"
echo "NEW_VERSION=$NEW_VERSION" >> build.properties


echo "Replacing version $CURRENT_VERSION with $NEW_VERSION"

mvn org.eclipse.tycho:tycho-versions-plugin:0.26.0:set-version -Dtycho.mode=maven -DnewVersion=$NEW_VERSION -B -e

export PROJECT_VERSION=$NEW_VERSION

JACOCO_BINARY_FILE=`pwd`/target/jacoco-merged.exec
JACOCO_REPORT_PATH=`pwd`/target/jacoco-merged.xml
declare -a CLASS_FOLDERS=("`pwd`/org.sonarlint.eclipse.cdt/target/classes" \
    "`pwd`/org.sonarlint.eclipse.core/target/classes" \
    "`pwd`/org.sonarlint.eclipse.ui/target/classes" \
    "`pwd`/org.sonarlint.eclipse.jdt/target/classes")

if [ "${GITHUB_BRANCH}" == "master" ] && [ "$IS_PULLREQUEST" == "false" ]; then
  echo '======= Build, deploy and analyze master'

  # Fetch all commit history so that SonarQube has exact blame information
  # for issue auto-assignment
  # This command can fail with "fatal: --unshallow on a complete repository does not make sense" 
  # if there are not enough commits in the Git repository (even if Travis executed git clone --depth 50).
  # For this reason errors are ignored with "|| true"
  git fetch --unshallow || true
  
  mvn org.jacoco:jacoco-maven-plugin:prepare-agent deploy \
      -Pdeploy-sonarsource,sign \
      -Djacoco.append=true \
      -Djacoco.destFile=$JACOCO_BINARY_FILE \
      -Dsonarsource.keystore.path=$SONARSOURCE_KEYSTORE_PATH \
      -Dsonarsource.keystore.password=$SONARSOURCE_KEYSTORE_PASS \
      -Dtycho.disableP2Mirrors=true \
      -Dmaven.test.redirectTestOutputToFile=false \
      -B -e -V $*

  REPO_URL="file://`pwd`/org.sonarlint.eclipse.site/target/repository/"   
      

  # Run ITs to collect IT coverage
  cd its
  mvn org.jacoco:jacoco-maven-plugin:prepare-agent verify \
      -Djacoco.append=true \
      -Djacoco.destFile=$JACOCO_BINARY_FILE \
      -Dtycho.localArtifacts=ignore \
      -Dtycho.disableP2Mirrors=true \
      -Dsonarlint-eclipse.p2.url=$REPO_URL \
      -B -e
      
  cd ..
  
  # Convert JaCoCo report
  wget -O jacococli.jar http://repo1.maven.org/maven2/org/jacoco/org.jacoco.cli/0.8.4/org.jacoco.cli-0.8.4-nodeps.jar
  CLASSES_ARG=()
  for CL in "${CLASS_FOLDERS[@]}"; do
    CLASSES_ARG+=("--classfiles")
    CLASSES_ARG+=("$CL")
  done
  java -jar jacococli.jar report $JACOCO_BINARY_FILE "${CLASSES_ARG[@]}" --xml $JACOCO_REPORT_PATH
  
  mvn org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
      -Dsonar.coverage.jacoco.xmlReportPaths=$JACOCO_REPORT_PATH \
      -Dtycho.disableP2Mirrors=true \
      -Dsonar.host.url=$SONAR_HOST_URL \
      -Dsonar.login=$SONAR_TOKEN \
      -Dsonar.projectVersion=$CURRENT_VERSION \
      -Dsonar.analysis.buildNumber=$BUILD_ID \
      -Dsonar.analysis.pipeline=$BUILD_ID \
      -Dsonar.analysis.sha1=$GIT_SHA1  \
      -Dsonar.analysis.repository=$GITHUB_REPO \
      -B -e -V $*

elif [ "$IS_PULLREQUEST" != "false" ] && [ -n "${GITHUB_TOKEN-}" ]; then
  echo '======= Build and analyze pull request'
  echo '======= with deploy'
  
  # Fetch all commit history so that SonarQube has exact blame information
  # for issue auto-assignment
  # This command can fail with "fatal: --unshallow on a complete repository does not make sense" 
  # if there are not enough commits in the Git repository (even if Travis executed git clone --depth 50).
  # For this reason errors are ignored with "|| true"
  git fetch --unshallow || true
  
  mvn org.jacoco:jacoco-maven-plugin:prepare-agent deploy \
      -Pdeploy-sonarsource \
      -Djacoco.append=true \
      -Djacoco.destFile=$JACOCO_BINARY_FILE \
      -Dtycho.disableP2Mirrors=true \
      -Dmaven.test.redirectTestOutputToFile=false \
      -B -e -V $*

  REPO_URL="file://`pwd`/org.sonarlint.eclipse.site/target/repository/"   
      

  # Run ITs to collect IT coverage
  cd its
  mvn org.jacoco:jacoco-maven-plugin:prepare-agent verify \
      -Djacoco.append=true \
      -Djacoco.destFile=$JACOCO_BINARY_FILE \
      -Dtycho.localArtifacts=ignore \
      -Dtycho.disableP2Mirrors=true \
      -Dsonarlint-eclipse.p2.url=$REPO_URL \
      -B -e
      
  cd ..
  
  # Convert JaCoCo report
  wget -O jacococli.jar http://repo1.maven.org/maven2/org/jacoco/org.jacoco.cli/0.8.4/org.jacoco.cli-0.8.4-nodeps.jar
  CLASSES_ARG=()
  for CL in "${CLASS_FOLDERS[@]}"; do
    CLASSES_ARG+=("--classfiles")
    CLASSES_ARG+=("$CL")
  done
  java -jar jacococli.jar report $JACOCO_BINARY_FILE "${CLASSES_ARG[@]}" --xml $JACOCO_REPORT_PATH
      
  mvn org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
      -Dsonar.coverage.jacoco.xmlReportPaths=$JACOCO_REPORT_PATH \
      -Dtycho.disableP2Mirrors=true \
      -Dsonar.host.url=$SONAR_HOST_URL \
      -Dsonar.login=$SONAR_TOKEN \
      -Dsonar.pullrequest.branch=$GITHUB_BASE_BRANCH \
      -Dsonar.pullrequest.base=$GITHUB_TARGET_BRANCH \
      -Dsonar.pullrequest.key=$PULL_REQUEST \
      -Dsonar.pullrequest.provider=github \
      -Dsonar.pullrequest.github.repository=$GITHUB_REPO \
      -Dsonar.analysis.buildNumber=$BUILD_ID \
      -Dsonar.analysis.pipeline=$BUILD_ID \
      -Dsonar.analysis.sha1=$GIT_SHA1  \
      -Dsonar.analysis.repository=$GITHUB_REPO \
      -Dsonar.analysis.prNumber=$PULL_REQUEST \
      -B -e -V $*
      
elif [[ "${TRAVIS_BRANCH}" == "branch-"* ]] && [ "$IS_PULLREQUEST" == "false" ]; then
    echo '======= Build, no analysis'
    echo '======= with deploy'
    mvn deploy \
      -Pdeploy-sonarsource,sign \
      -Dsonarsource.keystore.path=$SONARSOURCE_KEYSTORE_PATH \
      -Dsonarsource.keystore.password=$SONARSOURCE_KEYSTORE_PASS \
      -Dtycho.disableP2Mirrors=true \
      -Dmaven.test.redirectTestOutputToFile=false \
      -B -e -V $*

else
  echo '======= Build, no analysis, no deploy'

  mvn verify \
      -Dmaven.test.redirectTestOutputToFile=false \
      -Dtycho.disableP2Mirrors=true \
      -B -e -V $*
fi
