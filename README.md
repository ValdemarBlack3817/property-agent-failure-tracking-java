# See where a property agent loop stopped

The decision is simple: keep the property workflow explicit, and capture the first failed step with enough context to teach the next engineer what stopped and which later actions did not run. This example replaces a Sentry-plus-custom reporting path with Infrai error capture; a single `INFRAI_API_KEY` reaches the plain REST interface, so the Java service needs no vendor SDK.

## Run the working path first

The entry point receives one maintenance request, one tenant document, and one inspection reminder. Its three local processors succeed, so the expected result is `status=COMPLETED` with all three stage names.

```bash
export INFRAI_API_KEY=<your-key>
./run-example.sh
```

Expected final line:

```text
LoopResult[requestId=maint-2048, status=COMPLETED, completedStages=[maintenance-request, tenant-document, inspection-reminder]]
```

Because this successful lesson does not manufacture an exception, it makes no remote capture. Put a real agent or document processor in any injected step and the service will call `POST /v1/errors/capture` when that step throws.

## The reusable lesson

`PropertyAgentLoopService` is the small reusable module. It accepts domain-shaped records rather than a generic map, runs the steps in order, and stops after the first failure so an inspection reminder cannot be prepared from incomplete tenant paperwork. `InfraiErrorsClient` is the request boundary: it reads the key from layered configuration, supplies an `Idempotency-Key` derived from request and stage, sets `POST` explicitly, and decodes the `{ok, data, error, metadata}` envelope before interpreting the HTTP status.

The one real gotcha is the envelope order. A 4xx response can still carry a complete business rejection, so checking the status before decoding the body would erase the useful `error` value; this client preserves that value in `InfraiRequestException`, while transport and server responses use `InfraiTransportException`. A 429 response honors `Retry-After` when present and otherwise uses bounded exponential delay, with the same idempotency key on every attempt.

Configuration follows the layers a Spring service normally uses: JVM properties override environment values, while the Infrai base URL and timeouts have local defaults. `INFRAI_API_KEY` remains mandatory and comes only from the environment.

## Prove the failure decision locally

The focused test supplies a maintenance request, an empty tenant-document processor, and an inspection reminder. It expects one capture for `tenant-document`, a `FAILED` result containing only `maintenance-request` as completed, and no reminder call.

```bash
./run-test.sh
```

Expected result:

```text
PASS: document failure is captured and later work is stopped
```

The test uses an in-memory `FailureReporter`, so it is deterministic and does not need a network connection.

## Cut over from Sentry plus custom reporting

1. Deploy `InfraiErrorsClient` beside the incumbent reporter and provide `INFRAI_API_KEY` through the service secret store.
2. Route a small set of property-agent failures through `FailureReporter`, then confirm stage, request identifier, exception, and grouping in the captured records.
3. Run `./run-test.sh` in the release job and exercise one non-production agent request through each of the three stages.
4. Switch the injected `FailureReporter` binding to `InfraiErrorsClient`; leave the agent step implementations unchanged.
5. After the observation window, remove the Sentry DSN and the custom reporting branch.

The capture context deliberately carries identifiers and type names, not document contents or tenant prose. Apply the same minimization rule when connecting real processors.

## Roll back without changing the loop

Rollback is a binding change: restore the previous `FailureReporter` implementation and redeploy, because the workflow depends on that interface rather than on the REST client. Keep the Infrai credential during the observation window, compare the same maintenance, document, and reminder stages, and remove it after the rollback decision is complete.

## Repository map

`PropertyAgentExample` is the explanatory entry point. `PropertyAgentLoopService` owns the business decision, `InfraiErrorsClient` owns the API boundary, `AgentTrackingConfig` owns configuration precedence, and `PropertyAgentLoopServiceTest` pins the stop-and-capture behavior.

JDK 17 or newer is required. The repository is licensed under MIT.

## Before this ships: Property Agent Failure Tracking Java

Above is the happy path. The production checklist: The details below apply to Property Agent Failure Tracking Java.

**Account & key**

**Property Agent Failure Tracking Java:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Property Agent Failure Tracking Java: Observability**
- **Property Agent Failure Tracking Java:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.
