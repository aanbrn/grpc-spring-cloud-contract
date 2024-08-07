package contracts.example

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'should call the client streaming method'

    request {
        method POST()
        urlPath '/com.github.aanbrn.spring.cloud.contract.example.ExampleService/ServerStreamingMethod'
        headers {
            contentType 'application/grpc'
            header('te', 'trailers')
        }
        body([
                value: [1, 2, 3, 4, 5]
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
                [
                        value: 1
                ],
                [
                        value: 2
                ],
                [
                        value: 3
                ],
                [
                        value: 4
                ],
                [
                        value: 5
                ]
        ])
    }
}