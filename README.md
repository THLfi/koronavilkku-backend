## :warning: This project has been terminated and is no longer actively maintained: project has been archived and any usage should be done with caution! :warning:

# Finnish COVID-19 Application Backend

## Contents
- exposure-notification: The exposure notification service for receiving and distributing infection keys and parameters
- publish-token: The publish token generation and verification service for ensuring that only verified infections get reported

## Building
The project uses maven wrapper (mvnw).

This root package contains the others as modules, so you can build it all from here:
```
./mvnw clean package
```
... or on windows
```
mvnw.cmd clean package
```

Since the wrapper is in the root directory, you need to refer to it appropriately if going into the subprojects to build them individually:
```
cd exposure-notification
../mvnw clean package
```

## Database Migrations
Each service handles its own schema (en for exposure-notification and pt for publish-token), migrating them via Flyway as needed. 
They don't cross-use each other's data so service update/startup order should not matter. If desired, the services can be configured not to even see each other's schemas. 

In development environment, you may want to use the property `-Dspring.flyway.clean-on-validation-error=true`. 
This allows you to edit the schema definitions freely and have flyway simply reset the database when you make an incompatible change.
Obviously, you never want to deploy that setting into production environments though, as you will lose all data.

## Running Locally

### Requirements
- JDK 11
- PostgreSQL 12 (either directly, or docker for running in a container)

### Quick Startup
- The easiest way to get postgresql for development is via docker, for instance (using [the official image in docker hub](https://hub.docker.com/_/postgres)):
  ```
  docker run \
  --name covid19-db \
  -p 127.0.0.1:5433:5432 \
  -e POSTGRES_DB=exposure-notification \
  -e POSTGRES_USER=devserver \
  -e POSTGRES_PASSWORD=devserver-password \
  postgres:12
  ```
  - You can set the database to store the files in a location of your choosing by adding the parameter
    ```
    -v my-local-db-dir:/var/lib/postgresql/data
    ``` 
  - If you vary the parameters (username/password/port), just update them for each service `src/resources/application-dev.yml`
- Build both applications: `./mvnw clean package`
- Start exposure-notification service
    ```
    cd exposure-notification
    java \
      -Dspring.profiles.active=dev \
      -jar target/exposure-notification-1.0.0-SNAPSHOT.jar \
      fi.thl.covid19.exposurenotification.ExposureNotificationApplication
    ```
- Start publish-token service
    ```
    cd publish-token
    java \
      -Dspring.profiles.active=dev \
      -jar target/publish-token-1.0.0-SNAPSHOT.jar \
      fi.thl.covid19.publishtoken.PublishTokenApplication
    ```

## Contributing

We are grateful for all the people who have contributed so far. Due to tight schedule of Koronavilkku release we had no time to hone the open source contribution process to the very last detail. This has caused for some contributors to do work we cannot accept due to legal details or design choices that have been made during development. For this we are sorry.

**IMPORTANT** See further details from [CONTRIBUTING.md](CONTRIBUTING.md)
