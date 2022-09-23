#!/bin/bash

# Configure the default AWS CLI profile

aws configure set aws_access_key_id ${AWS_ACCESS_KEY_ID}
aws configure set aws_secret_access_key ${AWS_SECRET_ACCESS_KEY}
aws configure set default.region ${AWS_DEFAULT_REGION}

# If the endpoint URL hasn't already been set, then assume we're running the container with DMP

if [[ -z ${AWS_ENDPOINT_URL} ]]
then
    # Environment variables created via: https://dmp.fabric8.io/#start-links
    export AWS_ENDPOINT_URL="http://${S3_PORT_4566_TCP_ADDR}:${S3_PORT_4566_TCP_PORT}"
fi

# Create S3 buckets

aws s3 mb --endpoint-url ${AWS_ENDPOINT_URL} s3://thumbnails-source
aws s3 mb --endpoint-url ${AWS_ENDPOINT_URL} s3://thumbnails-target

# Uploads a file to S3

upload_to_s3() {
    aws s3 cp --endpoint-url ${AWS_ENDPOINT_URL} $1 s3://thumbnails-source/$(basename $1)
}

# Make function available to xargs

export -f upload_to_s3

# Add thumbnail images

find images -type f -name *.jpg -print0 \
    | xargs -0 -I {} bash -c 'upload_to_s3 "$@"' _ {}
