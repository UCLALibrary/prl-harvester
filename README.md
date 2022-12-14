# PRL Harvester

OAI-PMH harvester for the Pacific Rim Library.

## Running in Development

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
