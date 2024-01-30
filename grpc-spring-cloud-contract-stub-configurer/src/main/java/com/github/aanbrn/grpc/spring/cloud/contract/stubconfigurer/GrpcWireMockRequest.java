package com.github.aanbrn.grpc.spring.cloud.contract.stubconfigurer;

import com.github.tomakehurst.wiremock.common.Encoding;
import com.github.tomakehurst.wiremock.common.Strings;
import com.github.tomakehurst.wiremock.http.ContentTypeHeader;
import com.github.tomakehurst.wiremock.http.Cookie;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.QueryParameter;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.DynamicMessage;
import io.grpc.internal.GrpcUtil;
import lombok.NonNull;
import wiremock.com.google.common.base.Optional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.github.aanbrn.grpc.spring.cloud.contract.util.GrpcUtils.messageAsMap;
import static com.github.aanbrn.grpc.spring.cloud.contract.util.GrpcUtils.messagesAsList;

class GrpcWireMockRequest implements Request {

    private final String url;

    private final HttpHeaders headers = new HttpHeaders(
            new HttpHeader("Content-Type", GrpcUtil.CONTENT_TYPE_GRPC),
            new HttpHeader("te", GrpcUtil.TE_TRAILERS));

    private final String body;

    GrpcWireMockRequest(@NonNull MethodDescriptor methodDescriptor, DynamicMessage inputMessage) {
        this.url = "/" + methodDescriptor.getService().getFullName() + "/" + methodDescriptor.getName();
        if (inputMessage != null) {
            this.body = messageAsMap(inputMessage).toString();
        } else {
            this.body = null;
        }
    }

    GrpcWireMockRequest(@NonNull MethodDescriptor methodDescriptor, List<DynamicMessage> inputMessages) {
        this.url = "/" + methodDescriptor.getService().getFullName() + "/" + methodDescriptor.getName();
        if (inputMessages != null) {
            this.body = messagesAsList(inputMessages.iterator()).toString();
        } else {
            this.body = null;
        }
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public String getAbsoluteUrl() {
        return null;
    }

    @Override
    public RequestMethod getMethod() {
        return RequestMethod.POST;
    }

    @Override
    public String getScheme() {
        return null;
    }

    @Override
    public String getHost() {
        return null;
    }

    @Override
    public int getPort() {
        return 0;
    }

    @Override
    public String getClientIp() {
        return null;
    }

    @Override
    public String getHeader(String key) {
        HttpHeader header = headers.getHeader(key);
        if (header.isPresent()) {
            return header.firstValue();
        } else {
            return null;
        }
    }

    @Override
    public HttpHeader header(String key) {
        return headers.getHeader(key);
    }

    @Override
    public ContentTypeHeader contentTypeHeader() {
        return new ContentTypeHeader(GrpcUtil.CONTENT_TYPE_GRPC);
    }

    @Override
    public HttpHeaders getHeaders() {
        return headers;
    }

    @Override
    public boolean containsHeader(String key) {
        return headers.getHeader(key).isPresent();
    }

    @Override
    public Set<String> getAllHeaderKeys() {
        return headers.keys();
    }

    @Override
    public Map<String, Cookie> getCookies() {
        return Map.of();
    }

    @Override
    public QueryParameter queryParameter(String key) {
        return null;
    }

    @Override
    public byte[] getBody() {
        return Strings.bytesFromString(body);
    }

    @Override
    public String getBodyAsString() {
        return body;
    }

    @Override
    public String getBodyAsBase64() {
        return Encoding.encodeBase64(getBody());
    }

    @Override
    public boolean isMultipart() {
        return false;
    }

    @Override
    public Collection<Part> getParts() {
        return null;
    }

    @Override
    public Part getPart(String name) {
        return null;
    }

    @Override
    public boolean isBrowserProxyRequest() {
        return false;
    }

    @Override
    public Optional<Request> getOriginalRequest() {
        return null;
    }

    @Override
    public String getProtocol() {
        return null;
    }
}
