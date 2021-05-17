#!/usr/bin/env bash

HELPER_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

LIB_DIR=$(cd ${HELPER_DIR}"/../lib/"; pwd)
SWAN_SWIFTC=${LIB_DIR}/swan-swiftc

TEST_FILE=test.swift

ERROR_MESSAGE_FILE=/tmp/helper_error_message.out

RED="\033[31m"
GREEN="\033[32m"
ENDCOLOR="\033[0m"
BOLD="\033[1m"

test_directories() {
  trap 'exit 130' INT
  for dir in */ ; do
    [[ -d ${dir} ]] || continue
    cd ${dir}
    bash test.bash
    cd ..
  done
}

# assume already in the directory
test_directory() {

  (
    set -Ee
    function _catch {
      echo -e "${RED} \xE2\x9D\x8C FAILED${ENDCOLOR}"
      if [[ -f ${ERROR_MESSAGE_FILE} ]]; then
        cat ${ERROR_MESSAGE_FILE}
        rm ${ERROR_MESSAGE_FILE}
      fi
      exit 1
    }
    trap _catch ERR

    test_name=$(basename $PWD)
    # TODO: use relative path (HELPER_DIR and PWD) to support non-unique names
    if grep -Fxq ${test_name} ${HELPER_DIR}/skip.txt ; then
      echo -e "${BOLD}Skipping ${test_name}${ENDCOLOR}"
      return
    else
      echo -e "${BOLD}Testing ${test_name}${ENDCOLOR}"
    fi

    # create a tmp dir
    if [[ -z ${OUTPUT_DIR} ]]; then
      TEST_TMPDIR=$(mktemp -d)
    else
      rm -rf ${OUTPUT_DIR}; mkdir ${OUTPUT_DIR}
      TEST_TMPDIR=${OUTPUT_DIR}
    fi

    # copy the test file
    curr=${PWD}
    cp ${TEST_FILE} ${TEST_TMPDIR}
    cd ${TEST_TMPDIR}

    # do the SWAN steps

    # dump the SIL
    ${SWAN_SWIFTC} -- ${TEST_FILE} > ${ERROR_MESSAGE_FILE}
    rm ${ERROR_MESSAGE_FILE}
    # run analysis
    java -jar ${LIB_DIR}/driver.jar -j ${HELPER_DIR}/basic-spec.json -p swan-dir/ > ${ERROR_MESSAGE_FILE}
    rm ${ERROR_MESSAGE_FILE}
    # check annotations
    java -jar ${LIB_DIR}/annotation.jar swan-dir/ > ${ERROR_MESSAGE_FILE}
    rm ${ERROR_MESSAGE_FILE}
    
    # cleanup
    if [[ -z ${OUTPUT_DIR} ]]; then
      rm -rf ${TEMP_TMPDIR}
    fi
    cd ${curr}

    echo -e "${GREEN} \xE2\x9C\x94 PASSED${ENDCOLOR}"
  )
}