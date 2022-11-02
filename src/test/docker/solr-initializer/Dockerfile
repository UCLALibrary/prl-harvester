
ARG ALPINE_TAG
FROM alpine:${ALPINE_TAG}

# Install script dependencies

RUN apk add curl

# Copy over the executable

WORKDIR /usr/local/bin
COPY solr-initializer-assembly/docker-entrypoint.sh .
RUN chmod +x docker-entrypoint.sh

WORKDIR /root

ENTRYPOINT /usr/local/bin/docker-entrypoint.sh
