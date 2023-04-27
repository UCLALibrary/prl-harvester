# PRL Harvester

OAI-PMH harvester for the Pacific Rim Library.

## Dependencies

There are a couple of dependencies a developer must have installed in order to build this project.

* A [JDK](https://adoptium.net/marketplace/) (&gt;=17)
* [Maven](https://maven.apache.org/download.cgi) (&gt;=3.8)
* [Node.js](https://docs.npmjs.com/downloading-and-installing-node-js-and-npm) (&gt;=16) and [npm](https://docs.npmjs.com/downloading-and-installing-node-js-and-npm), for building the admin UI

## Configuration

The application is configured with environment variables, including several of the same [ones that PostgreSQL uses](https://www.postgresql.org/docs/current/libpq-envars.html):

Name | Description | Required? | Default
---|---|---|---
DB_CONNECTION_POOL_MAX_SIZE|The max size of the database connection pool|No|5
DB_RECONNECT_ATTEMPTS|The number of database reconnect attempts|No|2
DB_RECONNECT_INTERVAL|The length of the database reconnect interval (in milliseconds)|No|1000
HARVEST_TIMEOUT|The max amount of time that a harvest may take to complete (in milliseconds)|No|30000
HARVESTER_USER_AGENT|The User-Agent HTTP request header to use for outgoing requests|No|PRL Harvester
HTTP_PORT|The application's port|No|8888
PGDATABASE|The database name|No|db
PGHOSTADDR|The database host|No|localhost
PGPASSWORD|The database password|No|pass
PGPORT|The database port|No|5432
PGUSER|The database username|No|user
SOLR_CORE_URL|The Solr core URL|Yes|
LDAP_URL|The LDAP server URL|Yes|
LDAP_AUTH_QUERY|The LDAP query to authenticate user|Yes|
LDAP_USER_QUERY|The LDAP query to retrieve user info|Yes|
LDAP_ATTRIBUTE_KEY|The LDAP attribute key used to authorize user|Yes|
LDAP_ATTRIBUTE_VALUE|The LDAP attribute value used to authorize user|Yes|

## Running

### Option 1: Application container with local backing services using Docker Maven Plugin

The easiest way to spin up a working application stack on your local is with the following command, which also spins up a local instance of each required backing service:

    mvn pre-integration-test -DskipUTs

To view the console output of the harvester:

    docker container logs -f prl-harvester

To shut down:

    mvn docker:stop

### Option 2: Standalone application container using Docker Maven Plugin

First, build the application image:

    mvn package docker:build -Ddocker.filter=prl-harvester:%l -DskipUTs -DskipITs

Then, set environment variables on the command line via `-e` or `--env-file`; for example:

    docker container run -it -p 43210:8888 -e SOLR_CORE_URL="..." ... prl-harvester

### Option 3: Standalone JVM application using Vert.x Maven Plugin

Set environment variables in your shell:

    export SOLR_CORE_URL="..."
    ...

Then run:

    mvn vertx:initialize vertx:run

## The `debug` Maven Profile

The POM includes a `debug` profile for making it easier to debug test classes annotated with `@ExtendWith(VertxExtension.class)`.

To use:

1. Run something like this:

    ```bash
    mvn verify -Pdebug -Dmaven.failsafe.debug
    ```

2. Attach a debugger via JDWP to the ports specified in the Maven process output
3. Set breakpoints and enjoy

## Testing with production OAI-PMH data providers

Some of the integration tests run harvest jobs on OAI-PMH data providers running in production. These tests are tagged with `real-providers` and are disabled by default. To enable them, use the `test-real-providers` Maven profile:

```bash
mvn verify -Ptest-real-providers
```

## Independently testing the front-end application

The prl-harvester's front-end application can be developed independently of the main prl-harvester application.

To do this, follow the instructions in the front-end application's [README](src/main/frontend/README.md) file.

## Contact

We use an internal ticketing system, but we've left the GitHub [issues](https://github.com/UCLALibrary/prl-harvester/issues) open in case you'd like to file a ticket or make a suggestion.
