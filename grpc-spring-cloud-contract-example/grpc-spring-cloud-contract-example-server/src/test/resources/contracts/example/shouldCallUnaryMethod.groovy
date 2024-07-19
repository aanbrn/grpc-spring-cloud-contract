package contracts.example

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'should call the unary method'

    request {
        method POST()
        urlPath '/com.github.aanbrn.spring.cloud.contract.example.ExampleService/UnaryMethod'
        headers {
            contentType 'application/grpc'
            header('te', 'trailers')
        }
        body([
                value: 1
        ])
    }
    response {
        status OK()
        headers {
            contentType 'application/grpc'
            header('grpc-encoding', 'identity')
            header('grpc-accept-encoding', 'gzip')
        }
        body([
                value: 1
        ])
    }
}