package com.github.aanbrn.grpc.spring.cloud.contract.example.client;

import com.github.aanbrn.spring.cloud.contract.example.ExampleServiceGrpc.ExampleServiceStub;
import net.devh.boot.grpc.client.inject.GrpcClient;
import net.devh.boot.grpc.client.inject.GrpcClientBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@GrpcClientBean(clazz = ExampleServiceStub.class, client = @GrpcClient("example"))
class ExampleClientApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExampleClientApplication.class, args);
    }
}
