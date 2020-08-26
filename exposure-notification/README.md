# Exposure Notification Service for the Finnish COVID-19 Application

## Maintenance

### Properties
Full set of usable properties can be viewed in `src/main/resources/application.yml`

The most relevant and environment-specific properties are provided as ENV variables, prefixed with `EN_`.
* The main REST API listening port: `EN_SERVER_PORT`
* Port for monitoring services via Spring Actuator: `EN_MANAGEMENT_SERVER_PORT` 
* Database connection parameters: `EN_DATABASE_URL`, `EN_DATABASE_USERNAME`, `EN_DATABASE_PASSWORD`
* Demo-mode activation (distribute today's keys immediately, default false): `EN_DEMO_MODE`
* Local directory for caching batch files (defaults to a temp-directory): `EN_FILES`
* Address for reaching the publish-token service for token verification: `EN_PT_URL`
* Private key for signing diagnosis batches (see details below): `EN_SIGNING_PRIVATE_PKCS8`
* The signature version number (eg. v1 for testing, v2 for production): `EN_SIGNING_VERSION`
* Logback additional configuration: `EN_ROOT_LOG_LEVEL`, `EN_FI_THL_LOG_LEVEL`, `EN_LOG_INCLUDE`

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

`DiagnosisKeyController` logs all validated incoming requests on `INFO` level. 
This can be used to see what comes through for processing.

`ApiErrorHandler` logs all failed requests. 
The level is `WARN` if it's an "expected" issue, like client-side errors (403, 400, etc.) and `ERROR` if it's something unexpected (typically 500).

`DiagnosisKeyDao` logs all database operation on `INFO` level. 
When returning data from cache, those loggings are left out, so this should correspond to real DB traffic.

`BatchFileStorage` logs writing and deleting batch files to disk cache.

`MaintenanceService` logs numeric information about background tasks that clean up old data and pre-cache batches.

In addition, for class level logging, Mapped Diagnostic Context (MDC) is used to add pseudorandom correlation identifier (correlationId)
to every log message. CorrelationId binds all individual log messages of request flow together. For compatibility reasons,
correlationId is called errorId in http-responses.
If the client provides a user-visible error and errorId from response, it can be used to find the corresponding issue in the logs.

## Signing Keys
The diagnosis key batches are signed, so that the application can ensure that they come from a trusted source. 
The EN API requires the key to be ECDSA P-256 curve using the SHA256 hash function.

The public part of this signing key needs to be delivered to Google & Apple through official channels. 
Here, versions are differentiated with version numbers (eg. v1 for testing v2 for production).

The private part of the key should be kept secret. 
It is provided to the service through an environment variable (see above), along with the version number.

### Key Generation
Creating the key, as instructed by Google.
```
openssl ecparam -genkey -name prime256v1 -out​key​.pem
```

Java libraries want the private part in PKCS8, so convert it and provide this (the Base64-encoded PCKS8 key data) to the service as environment variable (see above).
```
openssl pkcs8 -topk8 -nocrypt -in key.pem -out key_pkcs8.pem
```

This is how you get the public part for Google and Apple.
```
openssl ec -in key_pkcs8.pem -pubout
```

### Temporary Dev Keys
If you just want to run the service and don't plan on verifying the signature, you can launch the service with argument `-Dcovid19.diagnosis.signature.randomize-key=true`.
This will generate a new key-pair on each launch, printing out the public part into log. 

## Caching
There are several forms of cache used by the application and can be configured through the application.yml.
Configurable values use [Duration parsing rules](https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html#parse-java.lang.CharSequence-).
1. Response cache at Proxy: Intermediate proxies can and should listen to the Cache-Control headers that give caching times as public. Most of the data doesn't change often, so common queries are cached thus.
    * covid19.diagnosis.response-cache.config-duration: Config API fetches for AppConfig & ExposureConfig
    * covid19.diagnosis.response-cache.status-duration: Diagnosis API fetches for list, current & status
    * covid19.diagnosis.response-cache.batch-duration: Diagnosis API fetches for batch files
1. Batch file cache at filesystem: All batch files are pre-generated into a local filesystem so they can be quickly served, even if the request gets past the cache.
1. Data access cache in RAM: Some status requests will always get through the proxy caches, so the most common database queries are cached in service RAM to ensure rapid responses.
    * covid19.diagnosis.data-cache.status-duration: database fetches needed for configuration or status information

## Configuration API
API for retrieving exposure configuration parameters as defined in:
* [Google documentation](https://developers.google.com/android/exposure-notifications/exposure-notifications-api#data-structures)
* [Apple documentation](https://developer.apple.com/documentation/exposurenotification/enexposureconfiguration)

### About Config Versions
Configuration API responses (AppConfiguration & ExposureConfiguration) return objects with version numbers. 
The API user should store these objects and request new ones using the version of the previously received configuration.
If the response comes back empty, the previously stored configuration is up-to-date and can be used as-is. 
If a config is returned, it should be stored and used instead.  

### Get Exposure Configuration
* **Cache:** This data changes unpredicably, but not often. Cacheable for 1h.
* **URL:** `/exposure/configuration/v1`
* **Method:** `GET`
* **URL Params:** None 
* **Query Params:** 
  * (Optional) previous: The version number of the previous exposure configuration the client has received. Empty for new client.
* **Request Body:** None
* **Success Response:**
  * Configuration has been updated 
    * Status: 200 OK
    * Body: JSON ExposureConfiguration object, adjusted from the Google/Apple definitions
        ```json
        {
          "version": 1,
          "minimumRiskScore": 1,
          "attenuationScores": [0,5,5,5,5,5,5,5],
          "daysSinceLastExposureScores": [1,1,1,1,1,1,1,1],
          "durationScores": [0,0,0,0,5,5,5,5],
          "transmissionRiskScores": [1,1,1,1,1,1,1,1],
          "durationAtAttenuationThresholds": [50,70]
        }
        ```
  * Configuration is already up to date
    * Status: 204 No Content

## Diagnosis API
API for posting Temporary Exposure Keys after a positive diagnosis as per 
* [Google Documentation for the API](https://developers.google.com/android/exposure-notifications/exposure-notifications-api)
* [Apple Documentation for the API](https://developer.apple.com/documentation/exposurenotification/setting_up_an_exposure_notification_server)

### About Batch IDs
The API relies on remembering current state of processing via batch IDs. 
From client perspective, these are only strings; don't parse or interpret them in any way. The back-end has full freedom to change the form of the IDs, so long as the API continues to function. 
Despite this, the batches do have an order and must be processed in the order given by backend, remembering the last successfully processed ID.

### Fetch the Batch ID for the Latest Diagnosis Key Batch
New clients that have yet to process anything should call this to get the server's current batch  ID for further `/diagnosis/list` requests. 
This batch does not need to be processed, as the new client should not have any encounters yet and hence cannot be exposed.
* **Cache:** This reply changes once per day, but the exact time is not set in stone.
* **URL:** `/diagnosis/v1/current`
* **Method:** `GET`
* **URL Params:** None 
* **Query Params:** None
* **Request Body:** None
* **Success Response:** 
  * Status: 200 OK
  * Body: a JSON object holding the latest batch ID
      ```json
      { "current": "12345679" }
      ```
    
### List Diagnosis Key Batches
Clients should periodically fetch the latest key batches, using this method to know if there is anything new to download.
The list <u>needs to be processed in the order it is given</u>, remembering the last successfully handled batch ID for the next list request. 
* **Cache:** This reply changes once per day, but the exact time is not set in stone.
* **URL:** `/diagnosis/v1/list`
* **Method:** `GET`
* **URL Params:** None 
* **Query Params:** 
  * (Mandatory) previous: the batch-id of the previous processed batch. If missing (new client), the client should request a batch  ID with `/diagnosis/current` first.
* **Request Body:** None
* **Success Response:** 
  * New content exists: 
    * Status: 200 OK
    * Body: A JSON object holding an <u>Ordered list</u> of batch IDs for fetching the files
      ```json
      { "batches": [ "12345678", "12345679" ] }
      ```
  * No new content exists: 
    * Status: 204 No content

### Status API
Status API is a one-call replacement for diagnosis key current & list fetches as well as the configuration fetch.
* **Cache:** This reply changes once per day, but the exact time is not set in stone.
* **URL:** `/diagnosis/v1/status`
* **Method:** `GET`
* **URL Params:** None 
* **Query Params:** 
  * (Optional) batch: the batch-id of the previous processed batch. Leave out if new client.
  * (Optional) app-config: the version of the previous AppConfiguration the client has received. Leave out if new client.
  * (Optional) exposure-config: the version of the previous ExposureConfiguration the client has received. Leave out if new client.
* **Request Body:** None
* **Success Response:** 
  * Status: 200 OK
  * Body: A JSON object containing:
    * batches: An <u>Ordered list</u> of batch IDs that the client should fetch. Empty list if nothing needs to be done.
    * appConfig: (Optional) The latest AppConfiguration, if it's been updated since the version given in app-config (or if no version was given). Empty if that version is still current.
    * exposureConfig: (Optional) The latest ExposureConfiguration, if it's been updated since the version given in exposure-config (or if no version was given). Empty if that version is still current.
    * A full sample where there are 2 new batches and both configs have changed:
      ```json
      { 
        "batches": [ "12345678", "12345679" ],
        "appConfig": {
          "version": 1,
          "tokenLength": 12,
          "diagnosisKeysPerSubmit": 14,
          "pollingIntervalMinutes": 240,
          "municipalityFetchIntervalHours": 48,
          "lowRiskLimit": 96,
          "highRiskLimit": 126 
        },
        "exposureConfig": {
          "version": 1,
          "minimumRiskScore": 1,
          "attenuationScores": [0,5,5,5,5,5,5,5],
          "daysSinceLastExposureScores": [1,1,1,1,1,1,1,1],
          "durationScores": [0,0,0,0,5,5,5,5],
          "transmissionRiskScores": [1,1,1,1,1,1,1,1],
          "durationAtAttenuationThresholds": [50,70]
        }
      }
      ```
    * A minimal sample where there's nothing to update:
      ```json
      { 
        "batches": [],
        "appConfig": null,
        "exposureConfig": null
      }
      ```

### Fetching a Single Diagnosis Key Batch
* **Cache:** This data never changes, but after 14 days of initial submit, it should no-longer be available.
* **URL:** `/diagnosis/v1/batch/{batch_id}`
* **Method:** `GET`
* **URL Params:** 
  * batch_id: the batch  ID, as provided by "List Diagnosis Key Batches" 
* **Query Params:** None
* **Request Body:** None
* **Success Response:**
  * Status: 200 OK
  * Body: The zip-file as specified in [Google Documentation for the file format](https://developers.google.com/android/exposure-notifications/exposure-key-file-format)
* **Failure Response:**
  * Such a batch is not available
    * Status: 404 Not Found

### Publishing Temporary Exposure Keys After a Positive Diagnosis of COVID-19
* **Cache:** None
* **URL:** `/diagnosis/v1`
* **Method:** `POST`
* **URL Params:** None
* **Query Params:** None
* **Headers:**
  * (Mandatory) `KV-Publish-Token`
    * Token received from health authority after verifying the infection 
    * In production mode, this is mandatory 
  * (Mandatory) `KV-Fake-Request`
    * 1 if sending a fake request. It will be validated for format, but token does not need to be real and nothing will be stored.
    * 0 if sending an actual save request. Token must be valid or the request will fail.
* **Request Body:**
  * keys: List of TemporaryExposureKey objects, as per [the API docs](https://developers.google.com/android/exposure-notifications/verification-system#metadata)
    * <u>Note, that the key data bytes need to be base64 encoded</u>
  * Sample Body 
    ```json
    {
      "keys": [{ 
          "keyData": "c9Uau9icuBlvDvtokvlNaA==",
          "transmissionRiskLevel": 5,
          "rollingStartIntervalNumber":  123456,
          "rollingPeriod": 144
        }, { 
          "keyData": "/MwsNfC4Rgnl8SxV3YWrqA==",
          "transmissionRiskLevel": 5,
          "rollingStartIntervalNumber":  123456,
          "rollingPeriod": 144
        }] 
    }
    ```
* **Success Response:**
  * Status: 200 OK
