package contracts.example

import org.springframework.cloud.contract.spec.Contract
import org.springframework.cloud.contract.verifier.http.ContractVerifierHttpMetaData

Contract.make {
    description 'should call the client streaming method'

    request {
        method POST()
        urlPath '/com.github.aanbrn.spring.cloud.contract.example.ExampleService/BidiStreamingMethod'
        headers {
            contentType 'application/grpc'
            header('te', 'trailers')
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
        metadata([
                verifierHttp: [
                        protocol: ContractVerifierHttpMetaData.Protocol.H2_PRIOR_KNOWLEDGE
                ]
        ])
    }
}
