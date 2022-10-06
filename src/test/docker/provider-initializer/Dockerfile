
ARG ALPINE_TAG
FROM alpine:${ALPINE_TAG}

# Install script dependencies

RUN apk add bash curl libxml2-utils nodejs npm tidyhtml util-linux
RUN npm install --global jsdom
ENV NODE_PATH="${NODE_PATH}:/usr/local/lib/node_modules/"

# Copy over the executable

WORKDIR /usr/local/bin
COPY docker-entrypoint.sh .
COPY clean-joai-response.js .
RUN chmod +x docker-entrypoint.sh clean-joai-response.js

WORKDIR /root

# Use bash so we can export shell functions

SHELL ["/bin/bash", "-c"]
ENTRYPOINT /usr/local/bin/docker-entrypoint.sh
