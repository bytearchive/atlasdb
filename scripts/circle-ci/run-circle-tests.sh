#!/usr/bin/env bash

set -x

function checkDocsBuild {
  cd docs/
  make html
}

# Container 1
CONTAINER_1=(':atlasdb-cassandra-tests:check')

# Container 2
CONTAINER_2=(':atlasdb-tests-shared:check' ':atlasdb-ete-tests:check')

#Container 3
CONTAINER_3=(':atlasdb-timelock-ete:check' ':lock-impl:check' ':atlasdb-dbkvs-tests:check')

CONTAINER_4=(':atlasdb-dropwizard-tests:check')

CONTAINER_5=(':atlasdb-cassandra-multinode-tests:check')

# Container 0 - runs tasks not found in the below containers
CONTAINER_0_EXCLUDE=("${CONTAINER_1[@]}" "${CONTAINER_2[@]}" "${CONTAINER_3[@]}" "${CONTAINER_4[@]}"  "${CONTAINER_5[@]}")

for task in "${CONTAINER_0_EXCLUDE[@]}"
do
    CONTAINER_0_EXCLUDE_ARGS="$CONTAINER_0_EXCLUDE_ARGS -x $task"
done

case $CIRCLE_NODE_INDEX in
    0) ./gradlew --profile --continue check $CONTAINER_0_EXCLUDE_ARGS ;;
    1) ./gradlew --profile --continue --parallel ${CONTAINER_1[@]} ;;
    2) ./gradlew --profile --continue --parallel ${CONTAINER_2[@]} ;;
    3) ./gradlew --profile --continue ${CONTAINER_3[@]} && checkDocsBuild ;;
    4) ./gradlew --profile --continue --parallel ${CONTAINER_4[@]} ;;
    5) ./gradlew --profile --continue --parallel ${CONTAINER_5[@]} ;;
esac
