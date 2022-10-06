
ARG ALPINE_TAG
FROM alpine:${ALPINE_TAG}

# Install script dependencies

RUN apk add aws-cli bash

# Copy over the executable

WORKDIR /usr/local/bin
COPY docker-entrypoint.sh .
RUN chmod +x docker-entrypoint.sh

WORKDIR /root

# Use bash so we can export shell functions

SHELL ["/bin/bash", "-c"]
ENTRYPOINT /usr/local/bin/docker-entrypoint.sh
