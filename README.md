# gRPC Extension for Spring Cloud Contract

It lets you use [Spring Cloud Contract](https://spring.io/projects/spring-cloud-contract) for testing
of [gRPC](https://grpc.io) services.

## gRPC Verifier

Intended for producer (server) side contract testing and implements a custom
gRPC-specific `org.springframework.cloud.contract.verifier.http.HttpVerifier` to be used in
the [Custom Mode](https://docs.spring.io/spring-cloud-contract/docs/current/reference/html/project-features.html#features-custom-mode).

## gRPC Stub Configurer

Intended for consumer (client) side contract testing and extends the
standard `org.springframework.cloud.contract.stubrunner.provider.wiremock.WireMockHttpServerStubConfigurer` to be used
to autoconfigure the stub runner and inject a gRPC-specific handler into the Jetty HTTP server running inside WireMock.

## Example

Use of this extension is illustrated on a simple gRPC service implemented in
the `grpc-spring-cloud-contract-example` module. Please take a look at it.

## Issues

The main problem is related to how to conveniently represent the request and response bodies in contracts and how the
standard test and stub generators handle them in case of the gRPC content type that is unknown for them. Especially, in
case of a streaming method, when there is a need to represent the body as an array of messages.

Currently, the test generator represents the request and response bodies improperly in case of an array of objects.
A couple of workarounds were introduced to handle it.

A more proper way probably is to extend the standard test and stub generators to add support for the gRPC content type,
but there is another issue with the test generator at least. It expects to get an implementation
of `org.springframework.cloud.contract.verifier.builder.SingleTestGenerator` via `META-INF/spring.factories`, but does
it using the default class loader, unfortunately. As long as, the related Maven plugin runs the test generator, so it's
not working even when the plugin is configured with additional dependencies.
