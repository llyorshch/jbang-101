# End-to-end tests for the clients API. Requires a running server:
#
#   hurl --test clients.hurl
#
# base_url defaults to http://localhost:8080 (set in mise.toml). To test another
# server, add --variable base_url=http://localhost:8181
#
# Entries run in order and share captured values. The suite creates its own
# clients, so it doesn't assume an empty database, but a throwaway database
# keeps things tidy (see README.md).


# --- UI -------------------------------------------------------------------

GET {{base_url}}/
HTTP 200
[Asserts]
header "Content-Type" contains "text/html"
header "X-Content-Type-Options" == "nosniff"


# --- Clients --------------------------------------------------------------

POST {{base_url}}/api/clients
{"name": "Acme University", "email": "ops@acme.example", "country": "ES"}
HTTP 201
[Captures]
client_id: jsonpath "$.id"
[Asserts]
header "Location" endsWith "/api/clients/{{client_id}}"
jsonpath "$.name" == "Acme University"
jsonpath "$.email" == "ops@acme.example"
jsonpath "$.country" == "ES"
jsonpath "$.status" == "PROSPECT"
jsonpath "$.createdAt" exists

GET {{base_url}}/api/clients/{{client_id}}
HTTP 200
[Asserts]
jsonpath "$.id" == {{client_id}}
jsonpath "$.status" == "PROSPECT"

GET {{base_url}}/api/clients
HTTP 200
[Asserts]
jsonpath "$[0].id" == {{client_id}}

# Validation errors are RFC 7807 problems with field errors

POST {{base_url}}/api/clients
{"name": "", "email": "not-an-email", "country": "spain"}
HTTP 400
[Asserts]
header "Content-Type" contains "application/problem+json"
jsonpath "$.status" == 400
jsonpath "$.errors.name" exists
jsonpath "$.errors.email" exists
jsonpath "$.errors.country" exists

# Malformed JSON

POST {{base_url}}/api/clients
Content-Type: application/json
```
{"name":
```
HTTP 400
[Asserts]
header "Content-Type" contains "application/problem+json"
body not contains "Exception"

GET {{base_url}}/api/clients/999999999
HTTP 404
[Asserts]
header "Content-Type" contains "application/problem+json"

# A PROSPECT client can't be offboarded

POST {{base_url}}/api/clients/{{client_id}}/offboardings
HTTP 409
[Asserts]
header "Content-Type" contains "application/problem+json"


# --- Onboarding: happy path with documents and amendment ------------------

POST {{base_url}}/api/clients/{{client_id}}/onboardings
HTTP 201
[Captures]
onb_id: jsonpath "$.id"
[Asserts]
jsonpath "$.clientId" == {{client_id}}
jsonpath "$.type" == "ONBOARDING"
jsonpath "$.stage" == "DRAFT"
jsonpath "$.subStage" == null
jsonpath "$.state" == "ACTIVE"
jsonpath "$.availableActions" count == 1
jsonpath "$.availableActions[0]" == "submit"

GET {{base_url}}/api/clients/{{client_id}}
HTTP 200
[Asserts]
jsonpath "$.status" == "ONBOARDING"

# Only one active case per client

POST {{base_url}}/api/clients/{{client_id}}/onboardings
HTTP 409

# Actions not available in DRAFT

POST {{base_url}}/api/onboardings/{{onb_id}}/actions/decide
{"outcome": "APPROVED"}
HTTP 409

POST {{base_url}}/api/onboardings/{{onb_id}}/actions/do-something-else
HTTP 400

# Draft -> open

POST {{base_url}}/api/onboardings/{{onb_id}}/actions/submit
{"note": "Draft complete"}
HTTP 200
[Asserts]
jsonpath "$.stage" == "OPEN"
jsonpath "$.subStage" == null
jsonpath "$.availableActions" contains "decide"
jsonpath "$.availableActions" contains "request-documents"
jsonpath "$.availableActions" contains "request-amendment"
jsonpath "$.availableActions" not contains "submit"

# Documents required

POST {{base_url}}/api/onboardings/{{onb_id}}/actions/request-documents
{"note": "Need proof of incorporation"}
HTTP 200
[Asserts]
jsonpath "$.stage" == "OPEN"
jsonpath "$.subStage" == "DOCUMENTS_REQUIRED"
jsonpath "$.availableActions" contains "confirm-documents"
jsonpath "$.availableActions" not contains "decide"
jsonpath "$.availableActions" not contains "request-documents"

# Can't decide while documents are pending

POST {{base_url}}/api/onboardings/{{onb_id}}/actions/decide
{"outcome": "APPROVED"}
HTTP 409

POST {{base_url}}/api/onboardings/{{onb_id}}/actions/confirm-documents
{"note": "Received by email"}
HTTP 200
[Asserts]
jsonpath "$.stage" == "OPEN"
jsonpath "$.subStage" == null
jsonpath "$.availableActions" contains "decide"

# Amendment required

POST {{base_url}}/api/onboardings/{{onb_id}}/actions/request-amendment
{"note": "Address does not match"}
HTTP 200
[Asserts]
jsonpath "$.subStage" == "AMENDMENT_REQUIRED"
jsonpath "$.availableActions" contains "confirm-amendment"
jsonpath "$.availableActions" not contains "decide"

POST {{base_url}}/api/onboardings/{{onb_id}}/actions/confirm-amendment
HTTP 200
[Asserts]
jsonpath "$.subStage" == null

# Documents can be requested again (repeatable sub-stage)

POST {{base_url}}/api/onboardings/{{onb_id}}/actions/request-documents
HTTP 200
[Asserts]
jsonpath "$.subStage" == "DOCUMENTS_REQUIRED"

POST {{base_url}}/api/onboardings/{{onb_id}}/actions/confirm-documents
HTTP 200
[Asserts]
jsonpath "$.subStage" == null

# Decide needs an outcome

POST {{base_url}}/api/onboardings/{{onb_id}}/actions/decide
{"note": "Forgot the outcome"}
HTTP 400

POST {{base_url}}/api/onboardings/{{onb_id}}/actions/decide
{"outcome": "MAYBE"}
HTTP 400

# Approve -> finished, succeeded

POST {{base_url}}/api/onboardings/{{onb_id}}/actions/decide
{"outcome": "APPROVED", "note": "All good"}
HTTP 200
[Asserts]
jsonpath "$.stage" == "FINISHED"
jsonpath "$.state" == "SUCCEEDED"
jsonpath "$.availableActions" isEmpty
jsonpath "$.endedAt" != null

GET {{base_url}}/api/onboardings/{{onb_id}}
HTTP 200
[Asserts]
jsonpath "$.state" == "SUCCEEDED"
jsonpath "$.history[0].action" == "submit"
jsonpath "$.history[0].note" == "Draft complete"
jsonpath "$.history[-1:].action" == "decide"
jsonpath "$.history[-1:].outcome" == "APPROVED"

GET {{base_url}}/api/clients/{{client_id}}
HTTP 200
[Asserts]
jsonpath "$.status" == "ACTIVE"

# Finished cases accept no more actions

POST {{base_url}}/api/onboardings/{{onb_id}}/actions/submit
HTTP 409

# An onboarding id is not an offboarding

GET {{base_url}}/api/offboardings/{{onb_id}}
HTTP 404

# An ACTIVE client can't be onboarded again

POST {{base_url}}/api/clients/{{client_id}}/onboardings
HTTP 409


# --- Offboarding ----------------------------------------------------------

POST {{base_url}}/api/clients/{{client_id}}/offboardings
HTTP 201
[Captures]
off_id: jsonpath "$.id"
[Asserts]
jsonpath "$.type" == "OFFBOARDING"
jsonpath "$.stage" == "OPENED"
jsonpath "$.state" == "ACTIVE"
jsonpath "$.availableActions" count == 1
jsonpath "$.availableActions[0]" == "start-checks"

GET {{base_url}}/api/clients/{{client_id}}
HTTP 200
[Asserts]
jsonpath "$.status" == "OFFBOARDING"

POST {{base_url}}/api/offboardings/{{off_id}}/actions/complete-checks
HTTP 409

POST {{base_url}}/api/offboardings/{{off_id}}/actions/start-checks
{"note": "Starting final checks"}
HTTP 200
[Asserts]
jsonpath "$.stage" == "CHECKS_ONGOING"
jsonpath "$.availableActions[0]" == "complete-checks"

POST {{base_url}}/api/offboardings/{{off_id}}/actions/complete-checks
{"note": "No pending balances"}
HTTP 200
[Asserts]
jsonpath "$.stage" == "FINISHED"
jsonpath "$.state" == "COMPLETED"
jsonpath "$.availableActions" isEmpty

GET {{base_url}}/api/clients/{{client_id}}
HTTP 200
[Asserts]
jsonpath "$.status" == "OFFBOARDED"

GET {{base_url}}/api/clients/{{client_id}}/cases
HTTP 200
[Asserts]
jsonpath "$" count == 2
jsonpath "$[0].id" == "{{off_id}}"
jsonpath "$[1].id" == "{{onb_id}}"


# --- Case diagrams --------------------------------------------------------

# Finished cases still have a diagram, rendered from history

GET {{base_url}}/api/onboardings/{{onb_id}}/diagram
HTTP 200
[Asserts]
header "Content-Type" == "image/png"
header "Cache-Control" contains "no-store"
bytes startsWith hex,89504E470D0A1A0A;

GET {{base_url}}/api/offboardings/{{off_id}}/diagram
HTTP 200
[Asserts]
header "Content-Type" == "image/png"
bytes startsWith hex,89504E470D0A1A0A;

# Case type must match the path

GET {{base_url}}/api/offboardings/{{onb_id}}/diagram
HTTP 404
[Asserts]
header "Content-Type" contains "application/problem+json"

GET {{base_url}}/api/onboardings/{{off_id}}/diagram
HTTP 404
[Asserts]
header "Content-Type" contains "application/problem+json"

GET {{base_url}}/api/onboardings/no-such-case/diagram
HTTP 404
[Asserts]
header "Content-Type" contains "application/problem+json"


# --- Re-onboarding an offboarded client -----------------------------------

POST {{base_url}}/api/clients/{{client_id}}/onboardings
HTTP 201
[Captures]
reonb_id: jsonpath "$.id"
[Asserts]
jsonpath "$.stage" == "DRAFT"
jsonpath "$.state" == "ACTIVE"
jsonpath "$.availableActions[0]" == "submit"

GET {{base_url}}/api/clients/{{client_id}}
HTTP 200
[Asserts]
jsonpath "$.status" == "ONBOARDING"

# Diagram of an active case

GET {{base_url}}/api/onboardings/{{reonb_id}}/diagram
HTTP 200
[Asserts]
header "Content-Type" == "image/png"
bytes startsWith hex,89504E470D0A1A0A;

GET {{base_url}}/api/clients/{{client_id}}/cases
HTTP 200
[Asserts]
jsonpath "$" count == 3
jsonpath "$[0].id" == "{{reonb_id}}"


# --- Onboarding: rejection ------------------------------------------------

POST {{base_url}}/api/clients
{"name": "Globex College", "email": "admin@globex.example", "country": "GB"}
HTTP 201
[Captures]
rejected_id: jsonpath "$.id"

POST {{base_url}}/api/clients/{{rejected_id}}/onboardings
HTTP 201
[Captures]
rej_onb_id: jsonpath "$.id"

POST {{base_url}}/api/onboardings/{{rej_onb_id}}/actions/submit
HTTP 200

POST {{base_url}}/api/onboardings/{{rej_onb_id}}/actions/decide
{"outcome": "REJECTED", "note": "Failed checks"}
HTTP 200
[Asserts]
jsonpath "$.stage" == "FINISHED"
jsonpath "$.state" == "FAILED"

GET {{base_url}}/api/clients/{{rejected_id}}
HTTP 200
[Asserts]
jsonpath "$.status" == "REJECTED"

# A REJECTED client can start a new onboarding

POST {{base_url}}/api/clients/{{rejected_id}}/onboardings
HTTP 201
[Asserts]
jsonpath "$.stage" == "DRAFT"

GET {{base_url}}/api/onboardings?active=true
HTTP 200
[Asserts]
jsonpath "$[?(@.clientId == {{rejected_id}})].state" == "ACTIVE"
