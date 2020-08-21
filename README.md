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
Each service handles their own schema (en for exposure-notification and pt for publish-token), migrating them via Flyway as needed. 
They don't cross-use each other's data so service update/startup order should not matter. If desired, the services can be configured not to even see each other's schemas. 

In development environment, you may want to use the property `-Dspring.flyway.clean-on-validation-error=true`. 
This allows you to edit the schema definitions freely and have flyway simply reset the database when you make an incompatible change.
Obviously, you never want to deploy that setting into production environments though (as you will lose all data), so it's not on by default.
