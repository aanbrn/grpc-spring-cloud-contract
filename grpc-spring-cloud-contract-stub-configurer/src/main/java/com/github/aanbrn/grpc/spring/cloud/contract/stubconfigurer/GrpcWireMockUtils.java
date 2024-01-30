package com.github.aanbrn.grpc.spring.cloud.contract.stubconfigurer;

import com.github.aanbrn.grpc.spring.cloud.contract.util.GrpcUtils;
import com.google.common.io.BaseEncoding;
import io.grpc.InternalMetadata;
import io.grpc.Metadata;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.ArrayUtils;
import wiremock.org.eclipse.jetty.server.Request;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

@UtilityClass
class GrpcWireMockUtils {

    static String extractMethodName(@NonNull Request request) {
        return GrpcUtils.extractMethodName(request.getRequestURI());
    }

    static Metadata extractHeaders(@NonNull Request request) {
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames == null || !headerNames.hasMoreElements()) {
            return InternalMetadata.newMetadata(ArrayUtils.EMPTY_BYTE_ARRAY);
        }

        List<byte[]> binaryValues = new ArrayList<>();

        do {
            String headerName = headerNames.nextElement();
            Enumeration<String> headerValues = request.getHeaders(headerName);
            if (headerValues != null && headerValues.hasMoreElements()) {
                do {
                    String headerValue = headerValues.nextElement();
                    if (headerName.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                        binaryValues.add(headerName.getBytes(StandardCharsets.US_ASCII));
                        binaryValues.add(BaseEncoding.base64().decode(headerValue));
                    } else {
                        binaryValues.add(headerName.getBytes(StandardCharsets.US_ASCII));
                        binaryValues.add(headerValue.getBytes(StandardCharsets.US_ASCII));
                    }
                } while (headerValues.hasMoreElements());
            }
        } while (headerNames.hasMoreElements());

        return InternalMetadata.newMetadata(binaryValues.toArray(new byte[][]{}));
    }
}
