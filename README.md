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
HARVESTER_USER_AGENT|The User-Agent HTTP request header to use for outgoing requests|No|PRL-Harvester
HTTP_PORT|The application's port|No|8888
LDAP_ATTRIBUTE_KEY|The LDAP attribute key used to authorize user|Yes|
LDAP_ATTRIBUTE_VALUE|The LDAP attribute value used to authorize user|Yes|
LDAP_AUTH_QUERY|The LDAP query to authenticate user|Yes|
LDAP_USER_QUERY|The LDAP query to retrieve user info|Yes|
LDAP_URL|The LDAP server URL|Yes|
OAIPMH_CLIENT_HTTP_TIMEOUT|The max amount of time that an OAI-PMH HTTP request may take to complete (in milliseconds)|No|60000
PGDATABASE|The database name|No|db
PGHOSTADDR|The database host|No|localhost
PGPASSWORD|The database password|No|pass
PGPORT|The database port|No|5432
PGUSER|The database username|No|user
SOLR_CORE_URL|The Solr core URL|Yes|
SOLR_UPDATE_MAX_BATCH_SIZE|The max batch size for Solr update queries|No|1000
SOLR_UPDATE_RETRY_COUNT|The retry count for Solr update queries|No|3

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

## Running one-off harvests with a local harvester instance pointed at production Solr

Certain situations may call for running (hopefully temporary) one-off harvests affecting production Solr using a local harvester instance; for example, if a repository has OAI-PMH spec compliance issues, you may want to run the harvester using a modified version of [XOAI](https://github.com/DSpace/xoai). Here's an example of such a procedure:

1. Install the modified XOAI to your local Maven repository; see [here](https://github.com/UCLALibrary/xoai/tree/for-repos-that-wont-take-just-a-resumptiontoken#building-the-project) for an example.

1. Update the `<harvester>` container run configuration in `pom.xml` to point to the prod Solr core:
    ```diff
    -<SOLR_CORE_URL>http://172.17.0.1:${test.solr.port}/solr/prl</SOLR_CORE_URL>
    +<SOLR_CORE_URL>https://prod-solr.mylibrary.edu/solr/prl</SOLR_CORE_URL>
    ```

1. Ensure that your IP address is allowed to use the /update handler of the prod Solr core.

1. Run a local instance of the harvester (and backing services, including PostgreSQL), configured with the user's local timezone and the version identifier of the modified XOAI:
    ```bash
    mvn clean pre-integration-test -DskipUTs -Denforcer.skip=true \
        -Duser.timezone=America/New_York -Dxoai.version=4.2.1-SNAPSHOT
    ```

1. Log in to the local PostgreSQL instance and set the next value of the primary key of the `institutions` table to be the ID of the institution of interest on the prod instance:
    ```bash
    # Retrieve the ID of the institution of interest

    PRL_HARVESTER_USERNAME=FIXME
    PRL_HARVESTER_PASSWORD=FIXME
    PRL_HARVESTER_CREDS=$(echo -n "$PRL_HARVESTER_USERNAME:$PRL_HARVESTER_PASSWORD" | base64)

    INSTITUTION_ID=$( \
        curl -s -H "Authorization: Basic $PRL_HARVESTER_CREDS" https://prl-harvester.library.ucla.edu/institutions \
        | jq '.[] | select(.name == "University of California Los Angeles") | .id' \
    )

    # Set the next value of the primary key of the institutions table

    export PGPASSWORD=$( \
        docker container exec pgsql /usr/bin/env \
        | grep POSTGRES_PASSWORD \
        | sed -e s/POSTGRES_PASSWORD=//g \
    )
    export PGPORT=$(docker container port pgsql | grep -o -m 1 "[[:digit:]]\+$")

    psql -h 0.0.0.0 -U postgres -w -c "SELECT setval(pg_get_serial_sequence('institutions', 'id'), $INSTITUTION_ID, false)"
    ```

1. Create an institution and harvest job locally:
    ```bash
    PRL_HARVESTER_LOCAL_INSTANCE=$(docker container port prl-harvester | cut -d ' ' -f 3 - | head -n 1)

    # Make sure to provide the name, description, location, etc. as they appear on production
    curl -X POST -H "Content-Type: application/json" "$PRL_HARVESTER_LOCAL_INSTANCE/institutions" --data-ascii '[{
        "name": "University of California Los Angeles",
        ...
    }]'

    # This job will run daily at 6:15 PM local time
    curl -X POST -H "Content-Type: application/json" "$PRL_HARVESTER_LOCAL_INSTANCE/jobs" --data-ascii "[{
        \"institutionID\": $INSTITUTION_ID,
        \"repositoryBaseURL\": \"https://digital.library.ucla.edu/catalog/oai\",
        \"sets\": [],
        \"metadataPrefix\": \"oai_dc\",
        \"scheduleCronExpression\": \"0 15 18 * * ?\",
        \"lastSuccessfulRun\": null
    }]"
    ```

1. Wait for the harvest job to complete (log stream will contain `Finished job <jobID>`):
    ```bash
    docker container logs -f prl-harvester
    ```

1. Shut down the local harvester:
    ```bash
    mvn docker:stop
    ```

## Contact

We use an internal ticketing system, but we've left the GitHub [issues](https://github.com/UCLALibrary/prl-harvester/issues) open in case you'd like to file a ticket or make a suggestion.
