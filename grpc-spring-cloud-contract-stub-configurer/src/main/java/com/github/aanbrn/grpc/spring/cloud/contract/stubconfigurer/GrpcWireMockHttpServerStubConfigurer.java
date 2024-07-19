package com.github.aanbrn.grpc.spring.cloud.contract.stubconfigurer;

import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.AdminRequestHandler;
import com.github.tomakehurst.wiremock.http.HttpServer;
import com.github.tomakehurst.wiremock.http.StubRequestHandler;
import com.github.tomakehurst.wiremock.jetty9.JettyHttpServerFactory;
import com.github.tomakehurst.wiremock.jetty94.Jetty94HttpServer;
import io.grpc.ServiceDescriptor;
import lombok.NonNull;
import lombok.val;
import org.springframework.cloud.contract.stubrunner.HttpServerStubConfiguration;
import org.springframework.cloud.contract.stubrunner.provider.wiremock.WireMockHttpServerStubConfigurer;
import wiremock.org.eclipse.jetty.server.handler.HandlerCollection;

import java.util.Collection;
import java.util.List;

import static shaded.com.google.common.base.Preconditions.checkArgument;

public abstract class GrpcWireMockHttpServerStubConfigurer extends WireMockHttpServerStubConfigurer {

    private final List<ServiceDescriptor> services;

    protected GrpcWireMockHttpServerStubConfigurer(@NonNull final Collection<ServiceDescriptor> services) {
        checkArgument(!services.isEmpty(), "Argument 'services' cannot be empty");

        this.services = List.copyOf(services);
    }

    @Override
    public WireMockConfiguration configure(
            @NonNull final WireMockConfiguration wireMockConfiguration,
            final HttpServerStubConfiguration httpServerStubConfiguration) {
        return wireMockConfiguration.httpServerFactory(
                new JettyHttpServerFactory() {
                    @Override
                    public HttpServer buildHttpServer(
                            final Options options,
                            final AdminRequestHandler adminRequestHandler,
                            final StubRequestHandler stubRequestHandler) {
                        return new Jetty94HttpServer(options, adminRequestHandler, stubRequestHandler) {
                            @Override
                            protected HandlerCollection createHandler(
                                    final Options options,
                                    final AdminRequestHandler adminRequestHandler,
                                    final StubRequestHandler stubRequestHandler) {
                                val handlers = new HandlerCollection();
                                handlers.addHandler(
                                        new GrpcWireMockHandler(
                                                super.createHandler(options, adminRequestHandler, stubRequestHandler),
                                                stubRequestHandler,
                                                services));
                                return handlers;
                            }
                        };
                    }
                });
    }
}
