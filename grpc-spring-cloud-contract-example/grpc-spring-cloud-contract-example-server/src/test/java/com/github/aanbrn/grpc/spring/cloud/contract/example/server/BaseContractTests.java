package com.github.aanbrn.grpc.spring.cloud.contract.example.server;

import com.github.aanbrn.grpc.spring.cloud.contract.example.server.BaseContractTests.TestContractConfiguration;
import com.github.aanbrn.grpc.spring.cloud.contract.verifier.GrpcHttpVerifier;
import io.grpc.BindableService;
import io.grpc.Channel;
import lombok.NonNull;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.contract.verifier.http.HttpVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestContractConfiguration.class)
@DirtiesContext
abstract class BaseContractTests {
    @TestConfiguration
    static class TestContractConfiguration {
        @Bean
        @DependsOn("grpcNameResolverRegistration")
        HttpVerifier grpcHttpVerifier(
                @NonNull GrpcChannelFactory grpcChannelFactory, @NonNull List<BindableService> grpcServices) {
            Channel channel = grpcChannelFactory.createChannel("example");
            return new GrpcHttpVerifier(channel, grpcServices);
        }
    }
}
