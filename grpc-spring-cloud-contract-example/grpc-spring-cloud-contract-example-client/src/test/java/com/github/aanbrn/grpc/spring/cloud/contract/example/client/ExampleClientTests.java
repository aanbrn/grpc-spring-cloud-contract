package com.github.aanbrn.grpc.spring.cloud.contract.example.client;

import com.github.aanbrn.grpc.spring.cloud.contract.example.client.ExampleClientTests.TestExampleStubConfigurer;
import com.github.aanbrn.grpc.spring.cloud.contract.stubconfigurer.GrpcWireMockHttpServerStubConfigurer;
import com.github.aanbrn.grpc.spring.cloud.contract.util.GrpcMultipleResponseFuture;
import com.github.aanbrn.grpc.spring.cloud.contract.util.GrpcSingleResponseFuture;
import com.github.aanbrn.spring.cloud.contract.example.*;
import com.github.aanbrn.spring.cloud.contract.example.ExampleServiceGrpc.ExampleServiceStub;
import io.grpc.stub.StreamObserver;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties.StubsMode.LOCAL;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureStubRunner(
        ids = "com.github.aanbrn:grpc-spring-cloud-contract-example-server:+:stubs",
        stubsMode = LOCAL,
        httpServerStubConfigurer = TestExampleStubConfigurer.class
)
class ExampleClientTests {
    static class TestExampleStubConfigurer extends GrpcWireMockHttpServerStubConfigurer {
        TestExampleStubConfigurer() {
            super(List.of(ExampleServiceGrpc.getServiceDescriptor()));
        }
    }

    @Autowired
    ExampleServiceStub exampleService;

    @Test
    void shouldCallUnaryMethod() throws Exception {
        val request =
                UnaryRequest
                        .newBuilder()
                        .setValue(1)
                        .build();
        val responseFuture = new GrpcSingleResponseFuture<UnaryResponse>();

        exampleService.unaryMethod(request, responseFuture);

        val response = responseFuture.get();
        assertThat(response).isNotNull();
        assertThat(response.getValue()).isEqualTo(request.getValue());
    }

    @Test
    void shouldCallClientStreamingMethod() throws Exception {
        val responseFuture = new GrpcSingleResponseFuture<ClientStreamingResponse>();

        val clientStreamObserver = exampleService.clientStreamingMethod(responseFuture);
        for (int value = 1; value <= 5; value++) {
            clientStreamObserver.onNext(
                    ClientStreamingRequest
                            .newBuilder()
                            .setValue(value)
                            .build());
        }
        clientStreamObserver.onCompleted();

        val response = responseFuture.get();
        assertThat(response).isNotNull();
        assertThat(response.getValueList()).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void shouldCallServerStreamingMethod() throws Exception {
        val responseFuture = new GrpcMultipleResponseFuture<ServerStreamingResponse>();

        exampleService.serverStreamingMethod(
                ServerStreamingRequest
                        .newBuilder()
                        .addValue(1)
                        .addValue(2)
                        .addValue(3)
                        .addValue(4)
                        .addValue(5)
                        .build(),
                responseFuture);

        val responses = responseFuture.get();
        assertThat(responses)
                .isNotNull()
                .hasSize(5);
        assertThat(responses.get(0))
                .isNotNull()
                .extracting("value")
                .isEqualTo(1L);
        assertThat(responses.get(1))
                .isNotNull()
                .extracting("value")
                .isEqualTo(2L);
        assertThat(responses.get(2))
                .isNotNull()
                .extracting("value")
                .isEqualTo(3L);
        assertThat(responses.get(3))
                .isNotNull()
                .extracting("value")
                .isEqualTo(4L);
        assertThat(responses.get(4))
                .isNotNull()
                .extracting("value")
                .isEqualTo(5L);
    }

    @Test
    void shouldCallBidiServerStreamingMethod() throws Exception {
        val responseFuture = new GrpcMultipleResponseFuture<BidiStreamingResponse>();

        val clientStreamObserver = exampleService.bidiStreamingMethod(responseFuture);
        for (int value = 1; value <= 5; value++) {
            clientStreamObserver.onNext(
                    BidiStreamingRequest
                            .newBuilder()
                            .setValue(value)
                            .build());
        }
        clientStreamObserver.onCompleted();

        val responses = responseFuture.get();
        assertThat(responses)
                .isNotNull()
                .hasSize(5);
        assertThat(responses.get(0))
                .isNotNull()
                .extracting("value")
                .isEqualTo(1L);
        assertThat(responses.get(1))
                .isNotNull()
                .extracting("value")
                .isEqualTo(2L);
        assertThat(responses.get(2))
                .isNotNull()
                .extracting("value")
                .isEqualTo(3L);
        assertThat(responses.get(3))
                .isNotNull()
                .extracting("value")
                .isEqualTo(4L);
        assertThat(responses.get(4))
                .isNotNull()
                .extracting("value")
                .isEqualTo(5L);
    }
}
