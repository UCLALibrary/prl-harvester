
# Our tagged version of Alpine is managed in the project's POM file as 'docker.alpine.version'
ARG ALPINE_TAG

# Pull our source image to use as the base for this image
FROM alpine:${ALPINE_TAG}

# Store some project metadata in the Docker image
LABEL ContainerName="${project.name}" ContainerSourceCode="${project.url}"

# Set timezone
RUN apk add alpine-conf \
    && setup-timezone -z "${user.timezone}" \
    && apk del alpine-conf

# Install packages and setup the application's user
WORKDIR "/opt/${project.artifactId}"
RUN apk add openjdk17 --quiet --no-cache \
  && addgroup -S "${project.artifactId}" \
  && adduser -S "${project.artifactId}" -G "${project.artifactId}" \
  && chown "${project.artifactId}":"${project.artifactId}" "/opt/${project.artifactId}"

# Copy files from our build environment into the container
COPY maven/"${project.artifactId}-${project.version}.jar" "/opt/${project.artifactId}/"
COPY configs/logback.xml "/etc/${project.artifactId}/logback.xml"

# The user that runs the application
USER "${project.artifactId}"

# The service's exposed port
EXPOSE ${HTTP_PORT}

# For container debugging
#ENTRYPOINT ["tail", "-f", "/dev/null"]

# Run the service
ENV JAR_PATH="/opt/${project.artifactId}/${project.artifactId}-${project.version}.jar"
ENV LOGGING_CONFIG="-Dlogback.configurationFile=/etc/${project.artifactId}/logback.xml"
CMD ["sh", "-c", "exec java ${JAVA_OPTS} ${LOGGING_CONFIG} -jar ${JAR_PATH}"]
