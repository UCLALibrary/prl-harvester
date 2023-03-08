# PRL Harvester

OAI-PMH harvester for the Pacific Rim Library.

## Dependencies

There are a couple of dependencies a developer must have installed in order to build this project.

* A [JDK](https://adoptium.net/marketplace/) (&gt;=17)
* [Maven](https://maven.apache.org/download.cgi) (&gt;=3.8)
* [Node.js](https://docs.npmjs.com/downloading-and-installing-node-js-and-npm) (&gt;=16) and [npm](https://docs.npmjs.com/downloading-and-installing-node-js-and-npm)

## Running in Development

    mvn vertx:initialize vertx:run

Building the front-end application requires Node.js version 16.0 or higher.

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

To do this, follow the instructions on the front-end application's [README](src/main/frontend/README.md) file.

## Contact

We use an internal ticketing system, but we've left the GitHub [issues](https://github.com/UCLALibrary/prl-harvester/issues) open in case you'd like to file a ticket or make a suggestion.
