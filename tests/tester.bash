#!/usr/bin/env bash

TESTS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

LIB_DIR=$(cd ${TESTS_DIR}"/../lib/"; pwd)
SWAN_SWIFTC=${LIB_DIR}/swan-swiftc
SIL_PACKAGES=()

TEST_FILE=test.swift
SPM_FILE=Package.swift

SPEC=${TESTS_DIR}/basic-spec.json

ERROR_MESSAGE_FILE=/tmp/tests_error_message.txt

RED="\033[31m"
GREEN="\033[32m"
ENDCOLOR="\033[0m"
BOLD="\033[1m"

if [ -z $LEVEL ]; then
  export LEVEL=0
fi

test_directories() {
  failed=false
  trap 'exit 130' INT
  for dir in */ ; do
    [[ -d ${dir} ]] || continue
    [[ -f "${dir}/test.bash" ]] || continue
    cd ${dir}
    LEVEL=$((LEVEL + 1))
    if [ -f $TEST_FILE ] || [ -f $SPM_FILE ]; then
      ./test.bash
    else
      test_directories
    fi
    if [ $? -ne 0 ]; then
      failed=true
    fi
    LEVEL=$((LEVEL - 1))
    cd ..
  done
  if [ $failed = true ]; then
    if [ $LEVEL = 0 ]; then
      echo -e "\n${RED} \xE2\x9D\x8C TESTS FAILED${ENDCOLOR}"
    fi
    exit 1
  fi
  if [ $LEVEL = 0 ]; then
    echo -e "\n${GREEN} \xE2\x9C\x94 ALL TESTS PASSED${ENDCOLOR}"
  fi
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
    # TODO: use relative path (TESTS_DIR and PWD) to support non-unique names
    if grep -Fxq ${test_name} ${TESTS_DIR}/skip.txt ; then
      echo -e "${BOLD}Skipping ${test_name}${ENDCOLOR}"
      return
    else
      # unsafeFlags doesn't work on Linux :(
      if [[ `uname` == 'Linux' ]]; then 
        echo -e "${BOLD}Skipping ${test_name}${ENDCOLOR}"
        return
      fi
      echo -e "${BOLD}Testing ${test_name}${ENDCOLOR}"
    fi

    # create a tmp dir
    if [[ -z ${OUTPUT_DIR} ]]; then
      TEST_TMPDIR=$(mktemp -d)
    else
      rm -rf ${OUTPUT_DIR}; mkdir ${OUTPUT_DIR}
      TEST_TMPDIR=${OUTPUT_DIR}
    fi

    # copy everything to the tmp dir
    count=`ls -1 *.swift 2>/dev/null | wc -l`
    if [ $count != 0 ]; then 
      cp *.swift ${TEST_TMPDIR}
    fi
    count=`ls -1 *.swirl 2>/dev/null | wc -l`
    if [ $count != 0 ]; then 
      cp *.swirl ${TEST_TMPDIR}
    fi
    if [ -f ${SPM_FILE} ]; then
      mkdir ${TEST_TMPDIR}/Sources/
      cp -r Sources/ ${TEST_TMPDIR}/Sources/
      cp ${SPM_FILE} ${TEST_TMPDIR}
    fi
    if [[ ! -z ${CUSTOM_SPEC} ]]; then
      SPEC=${CUSTOM_SPEC}
      cp ${CUSTOM_SPEC} ${TEST_TMPDIR}
    fi

    curr=${PWD}
    cd ${TEST_TMPDIR}

    # dump the SIL
    if [ -f $TEST_FILE ]; then
      ${SWAN_SWIFTC} -- ${TEST_FILE} > ${ERROR_MESSAGE_FILE}
    else
      swift package clean
      python3 ${TESTS_DIR}/swan-spm.py > ${ERROR_MESSAGE_FILE} 
    fi
    rm ${ERROR_MESSAGE_FILE}

    # copy any .swirl to swan-dir
    count=`ls -1 *.swirl 2>/dev/null | wc -l`
    if [ $count != 0 ]; then 
      cp *.swirl swan-dir/
    fi

    # copy any imported files to swan-dir
    for f in ${SIL_PACKAGES}; do
      cp ${TESTS_DIR}/sil-packages/${f} swan-dir/
    done
    
    # run analysis
    java -jar ${LIB_DIR}/driver.jar -j ${SPEC} -p swan-dir/ ${DRIVER_OPTIONS} > ${ERROR_MESSAGE_FILE}
    mv ${ERROR_MESSAGE_FILE} driver_log.txt

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

import() {
  SIL_PACKAGES+=$1
}