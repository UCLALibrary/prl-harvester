
ARG ALPINE_TAG
FROM alpine:${ALPINE_TAG}

# Install build dependencies

RUN apk add aws-cli bash

# Copy over the executable and thumbnail images

WORKDIR /usr/local/bin
COPY docker-entrypoint.sh .
RUN chmod +x docker-entrypoint.sh

WORKDIR /root
COPY images ./images

SHELL ["/bin/bash", "-c"]
ENTRYPOINT /usr/local/bin/docker-entrypoint.sh
