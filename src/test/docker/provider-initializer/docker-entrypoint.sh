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
        -F 'repositoryName=Test Institution' \
        -F 'adminEmail=test@example.edu' \
        -F 'repositoryDescription=' \
        -F 'namespaceIdentifier=' \
    | render_joai_response

    echo
}

add_metadata_dir() {
    set_spec="$(basename ${1})"

    echo -e "`bold '--- Metadata validation ---'`\n"

    # This jOAI endpoint seems to require the data as application/x-www-form-urlencoded, so we can't pass individual input fields via -F
    # The call to sed is necessary because html-tidy complains about an unmatched </form>, and <br> would complicate rendering the table as plaintext
    curl -s -X POST ${JOAI_SERVICE_URL}/admin/metadata_dir-validate.do \
        --data-raw "command=addMetadataDir&dirNickname=Test+Set+%22${set_spec}%22&dirMetadataFormat=oai_dc&dirPath=%2Fjoai%2Fdata%2F${set_spec}&metadataNamespace=http%3A%2F%2Fwww.openarchives.org%2FOAI%2F2.0%2Foai_dc%2F&metadataSchema=http%3A%2F%2Fwww.openarchives.org%2FOAI%2F2.0%2Foai_dc.xsd" \
    | sed -e 's/<\/form>//' -e 's/<br>/ /' \
    | render_joai_response

    echo
}

define_set() {
    set_spec="$(basename $1)"
    selective_harvest_url="${JOAI_SERVICE_URL}/provider?verb=ListRecords&metadataPrefix=oai_dc&set=${set_spec}"

    echo -e "`bold '--- Set definition ---'`\n"

    # This jOAI endpoint seems to require the data as application/x-www-form-urlencoded, so we can't pass individual input fields via -F
    # The call to sed is necessary because html-tidy complains about an unmatched </form>, and <br> would complicate rendering the table as plaintext
    curl -s -X POST ${JOAI_SERVICE_URL}/admin/set_definition-validate.do \
        --data-raw "setName=Test+Set+%22${set_spec}%22&setSpec=${set_spec}&include_radio=include_radio_3&includedDirs=%2Fjoai%2Fdata%2F${set_spec}&limit_radio=limit_radio_1&exclude_radio=exclude_radio_1" \
    | sed -e 's/<\/form>//' -e 's/<br>/ /' \
    | render_joai_response

    echo -e "\nPlease verify that the selective harvest \"${selective_harvest_url}\" appears as expected:\n"

    curl -s ${selective_harvest_url} \
    | xmllint --format -

    echo
}

render_joai_response() {
    tidy -q -c -i --show-body-only yes --show-warnings no --show-errors 0 --wrap 0 \
    | clean-joai-response.js \
    | column -t -s $'\t'
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
