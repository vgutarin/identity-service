# java-service-template

## identity-api
- defines interfaces
- contains minimum of code

## identity-logic
library  
- implements api 
- contains business logic
- can be "embedded" into some project as an api implementation

## identity-rest-service
web service implements api and can be started as separate web (spring boot) app
- implements api
- uses `identity-logic` to run logic

## identity-rest-client
library can be used instead of "embedded" `identity-logic` implementation.
- implements api
- makes query to `identity-rest-service`


## TODO

- rework User to User { uniqueId, details } 