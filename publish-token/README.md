# Diagnosis Publish Token Generation Service for the Finnish COVID-19 Application

## Maintenance

### Properties
Full set of usable properties can be viewed in `src/main/resources/application.yml`

The most relevant and environment-specific properties are provided as ENV variables, prefixed with `PT_`.
* The main REST API listening port: `PT_SERVER_PORT`
* Port for monitoring services via Spring Actuator: `PT_MANAGEMENT_SERVER_PORT` 
* Database connection parameters: `PT_DATABASE_URL`, `PT_DATABASE_USERNAME`, `PT_DATABASE_PASSWORD`
* Gateway for SMS sending service: `PT_SMS_GATEWAY_URL`
* Logback additional configuration: `PT_ROOT_LOG_LEVEL`, `PT_FI_THL_LOG_LEVEL`, `PT_LOG_INCLUDE`

### Database
The service is built to use PostgreSQL database, version 12. 

The service automatically creates and migrates the schema (named `pt`) on startup, using Flyway. 
If there are incompatible migrations, the startup will fail. 

Connection parameters are given as environment variables (see above).

### Monitoring
The services uses Spring Actuator to provide basic monitoring information.

In addition, logs should be used to identify issues. The principle of log levels in the service:
* `ERROR`: Something broke that shouldn't have. These should never appear normally. Each one is worth investigating.
* `WARN`: Something went wrong, but the situation is handled. These can be expected to happen, but if some warnings start spiking, it could be worth investigation.
* `INFO`: Normal runtime logging of expected events.
* `DEBUG`: Detailed logs of execution, only interesting in special cases.

`PublishTokenGenerationController` and `PublishTokenVerificationController` log all validated incoming requests on `INFO` level.
This can be used to see what comes through for processing.

`ApiErrorHandler` logs all failed requests. 
The level is `WARN` if it's an "expected" issue, like client-side errors (403, 400, etc.) and `ERROR` if it's something unexpected (typically 500).

`PublishTokenDao` logs all database write operations on `INFO` level.

`PublishTokenService` logs periodic maintenance runs where it cleans things from the DB

`SmsService` logs attempts to send out a token via SMS

In addition, for class level logging, Mapped Diagnostic Context (MDC) is used to add pseudorandom correlation identifier (correlationId)
to every log message. CorrelationId binds all individual log messages of request flow together. For compatibility reasons,
correlationId is called errorId in http-responses.
If the client provides a user-visible error and errorId from response, it can be used to find the corresponding issue in the logs.

## Publish Token Generation API
API meant for health authorities, for creating a publish-token for a diagnosis report after verifying the infection.

Deprecated components will be removed in next api-version.

### Create a New Token after a Verified Diagnosis of COVID-19
* **URL:** `/publish-token/v1`
* **Method:** `POST`
* **URL Params:** None
* **Query Params:** None
* **Headers:**
  * (Mandatory) `KV-Request-Service` Calling service name. (With default configuration this is resolved automatically on load balancer and calling client does not need to worry about this.)
  * (Optional) `KV-Validate-Only` Boolean (true or false, if missing false). This is for API verification tests: request is validated and a token created, it isn't stored in the database (activated) or sent via SMS. (If validate-only in the request body is true, this has no effect)
* **Request Body:** 
  * (Mandatory) requestUser: User who made the request (for auditing). Plain-text identifier, unique within the requesting service.
  * (Mandatory) symptomsOnset: (Estimated) date of initial onset of symptoms. This will affect the risk classification of the reported keys.
  * (Optional) patientSmsNumber: Phone number to delivering the token via SMS to the patient. This will not be stored.
  * (Optional) symptomsExist: indicates if patient has symptoms. If this is set as true, then symptomsOnset should indicate start of symptoms only.
  * **DEPRECATED** (Optional) validateOnly: Boolean (null defaults to false). This is for API verification tests: request is validated and a token created, it isn't stored in the database (activated) or sent via SMS. (If validate-only in the header is true, this has no effect)
  * Sample Body 
      ```json
      { 
        "requestUser": "USER2342",
        "symptomsOnset": "2020-07-01",
        "patientSmsNumber": "+358401234567",
        "symptomsExist": true
      }
      ```
* **Success Response:**
  * Status: 200 OK
  * Body: An object holding the token and metadata for its validity.
  * Sample Response: 
    ```json
    {
      "token": "123456789",
      "createTime": "2020-07-01T15:13:20.050472Z",
      "validThroughTime": "2020-07-02T15:13:20.050472Z"
    }
    ```
* **Failure Response:**
    * Error when communicating with sms-gateway
    * Status: 502 Bad Gateway
    
### Fetch Tokens Created by a User
Request tokens created by a single user, so that the user may re-check the code in case of mistakes or misreads.
* **URL:** `/publish-token/v1/{user}`
* **Method:** `GET`
* **URL Params:**
  * (Mandatory) user: user who created the tokens
* **Headers:**
  * (Mandatory) `KV-Request-Service` Calling service name. (With default configuration this is resolved automatically on load balancer and calling client does not need to worry about this.)
* **Query Params:** None
* **Request Body:** None
* **Success Response:**
  * Status: 200 OK
  * Body: An object holding the tokens created by the user, with their respective metadata.
  * Sample Response: 
    ```json
    { "publishTokens": [{
        "token": "987654321",
        "createTime": "2020-07-01T13:10:10.000Z",
        "validThroughTime": "2020-07-02T13:10:10.000Z"
      },{
        "token": "123456789",
        "createTime": "2020-07-01T15:13:20.000Z",
        "validThroughTime": "2020-07-02T15:13:20.000Z"
      }]
    }
    ```

## Publish Token Verification API
This is an internal API that the exposure-notification service uses for verifying the publish-token on diagnosis key commit.

### Verify Token
* **URL:** `/verification/v1`
* **Method:** `GET`
* **URL Params:** None
* **Query Params:** None
* **Headers:**
  * (Mandatory) `KV-Publish-Token` Token to be verified
* **Request Body:** None
* **Success Response (Active token found):**
  * Status: 200 OK
  * Body: A JSON object holding 
    * id: Unique ID for the verified token. The token itself can be re-used, but id will appear only once.
    * symptomsOnset: (Estimated) time of initial onset of symptoms, associated with the verified token
    * symptomsExist: Boolean for existence of symptoms. Might be null, which means existence of symptoms is unknown i.e. data is missing.
  * Sample Response: 
    ```json
    { 
      "id": 1243,
      "symptomsOnset": "2020-07-01T13:10:10.000Z",
      "symptomsExist": true
    }
    ```
* **Verification Rejection Response (No active token found):**
  * Status: 204 No Content
  
