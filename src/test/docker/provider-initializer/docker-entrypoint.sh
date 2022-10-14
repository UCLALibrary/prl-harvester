#!/bin/bash

set -o pipefail

# If the service URL hasn't already been set, then assume we're running the container with DMP

if [[ -z ${JOAI_SERVICE_URL} ]]
then
    # Environment variables created via: https://dmp.fabric8.io/#start-links
    export JOAI_SERVICE_URL="http://${PROVIDER_PORT_8080_TCP_ADDR}:${PROVIDER_PORT_8080_TCP_PORT}"
fi

configure_repository_info() {
    echo -e "`bold '--- Repository info configuration ---'`\n"

    curl -s -X POST ${JOAI_SERVICE_URL}/admin/repository_info-validate.do \
        --data-urlencode 'repositoryName=Test Institution' \
        --data-urlencode 'adminEmail=test@example.edu' \
    | render_joai_response

    echo
}

add_metadata_dir() {
    set_spec="$(basename ${1})"

    echo -e "`bold '--- Metadata validation ---'`\n"

    curl -s -X POST ${JOAI_SERVICE_URL}/admin/metadata_dir-validate.do \
        --data-raw "command=addMetadataDir&dirMetadataFormat=oai_dc" \
        --data-urlencode "dirNickname=Test Set \"${set_spec}\"" \
        --data-urlencode "dirPath=/joai/data/${set_spec}" \
    | render_joai_response

    echo
}

define_set() {
    set_spec="$(basename $1)"
    selective_harvest_url="${JOAI_SERVICE_URL}/provider?verb=ListRecords&metadataPrefix=oai_dc&set=${set_spec}"

    echo -e "`bold '--- Set definition ---'`\n"

    curl -s -X POST ${JOAI_SERVICE_URL}/admin/set_definition-validate.do \
        --data-urlencode "setName=Test Set \"${set_spec}\"" \
        --data-urlencode "includedDirs=/joai/data/${set_spec}" \
        --data-urlencode "setSpec=${set_spec}" \
        --data-raw "include_radio=include_radio_3&limit_radio=limit_radio_1&exclude_radio=exclude_radio_1" \
    | render_joai_response

    echo -e "\nPlease verify that the selective harvest \"${selective_harvest_url}\" appears as expected:\n"

    curl -s ${selective_harvest_url} \
    | xmllint --format -

    echo
}

render_joai_response() {
    clean-joai-response.js | column -t -s $'\t'
}

bold() {
    echo -e "\e[1m${1}\e[0m"
}

# Make functions available to xargs

export -f add_metadata_dir bold define_set render_joai_response

# Do the work

configure_repository_info && \
find records -mindepth 1 -type d -print0 \
    | xargs -0 -I {} bash -c 'add_metadata_dir "$@" && define_set "$@"' _ {}
