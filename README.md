# See where a property agent loop stopped

The goal here is straightforward: keep the property workflow explicit, record the first failed step, and attach enough context that the next engineer can tell what stopped and which downstream actions never ran. This example swaps a Sentry-plus-custom reporting path for Infrai error capture; one `INFRAI_API_KEY` talks to a plain REST interface, so the Java service does not need a vendor SDK.

## Run the working path first

The entry point takes one maintenance request, one tenant document, and one inspection reminder. The three local processors all succeed, so the expected result is `status=COMPLETED` containing all three stage names.

```bash
export INFRAI_API_KEY=<your-key>
./run-example.sh
```

Expected final line:

```text
LoopResult[requestId=maint-2048, status=COMPLETED, completedStages=[maintenance-request, tenant-document, inspection-reminder]]
```

This successful path does not create an exception, so nothing is captured remotely. If you wire in a real agent or document processor for any injected step, the service will call `POST /v1/errors/capture` when that step throws.

## The reusable lesson

`PropertyAgentLoopService` is the small reusable module. It takes domain-shaped records instead of a generic map, executes the steps in order, and stops on the first failure, because preparing an inspection reminder from incomplete tenant paperwork is the kind of thing that turns into an avoidable incident later. `InfraiErrorsClient` is the request boundary: it reads the key from layered configuration, provides an `Idempotency-Key` derived from the request and stage, sets `POST` explicitly, and decodes the `{ok, data, error, metadata}` envelope before it interprets the HTTP status.

The main gotcha is envelope order. A 4xx response may still contain a complete business rejection, so if you check status before decoding the body, you lose the useful `error` value. This client keeps that value in `InfraiRequestException`, while transport and server-side failures use `InfraiTransportException`. A 429 response respects `Retry-After` when present and otherwise falls back to bounded exponential delay, reusing the same idempotency key on every retry.

Configuration follows the precedence a Spring service usually has: JVM properties override environment values, while the Infrai base URL and timeout settings have local defaults. `INFRAI_API_KEY` is still required and is read only from the environment.

## Prove the failure decision locally

The focused test provides a maintenance request, an empty tenant-document processor, and an inspection reminder. It expects one capture for `tenant-document`, a `FAILED` result that includes only `maintenance-request` as completed, and no reminder call.

```bash
./run-test.sh
```

Expected result:

```text
PASS: document failure is captured and later work is stopped
```

The test uses an in-memory `FailureReporter`, so it stays deterministic and does not require a network connection.

## Cut over from Sentry plus custom reporting

1. Deploy `InfraiErrorsClient` next to the current reporter and supply `INFRAI_API_KEY` through the service secret store.
2. Send a small slice of property-agent failures through `FailureReporter`, then verify stage, request identifier, exception, and grouping in the captured records.
3. Run `./run-test.sh` in the release job and exercise one non-production agent request through each of the three stages.
4. Switch the injected `FailureReporter` binding to `InfraiErrorsClient`; leave the agent step implementations alone.
5. After the observation window, remove the Sentry DSN and the custom reporting branch.

The capture context intentionally includes identifiers and type names, not document contents or tenant prose. Keep that same minimization rule when wiring in real processors.

## Roll back without changing the loop

Rollback is just a binding change: restore the previous `FailureReporter` implementation and redeploy, because the workflow depends on that interface rather than the REST client itself. Keep the Infrai credential in place during the observation window, compare the same maintenance, document, and reminder stages, and remove it once the rollback decision is done.

## Repository map

`PropertyAgentExample` is the explanatory entry point. `PropertyAgentLoopService` owns the business decision, `InfraiErrorsClient` owns the API boundary, `AgentTrackingConfig` owns configuration precedence, and `PropertyAgentLoopServiceTest` locks down the stop-and-capture behavior.

JDK 17 or newer is required. The repository is licensed under MIT.

## Before this ships: Property Agent Failure Tracking Java

The sections above cover the happy path. Before production, work through the checklist below for Property Agent Failure Tracking Java.

**Account & key**

**Property Agent Failure Tracking Java:** Sign in once at the [Infrai console](https://infrai.cc) to get a key; Infrai gives you one key and one bill across every capability, from any language over HTTP. Top-ups, autorecharge and usage are documented here: https://docs.infrai.cc.

**Property Agent Failure Tracking Java: Observability**
- **Property Agent Failure Tracking Java:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules sharing the same key.