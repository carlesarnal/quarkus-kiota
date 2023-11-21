package com.microsoft.kiota.http;

import com.microsoft.kiota.*;
import com.microsoft.kiota.authentication.AuthenticationProvider;
import com.microsoft.kiota.serialization.Parsable;
import com.microsoft.kiota.serialization.ParsableFactory;
import com.microsoft.kiota.serialization.ParseNode;
import com.microsoft.kiota.serialization.ParseNodeFactory;
import com.microsoft.kiota.serialization.ParseNodeFactoryRegistry;
import com.microsoft.kiota.serialization.SerializationWriterFactory;
import com.microsoft.kiota.serialization.SerializationWriterFactoryRegistry;
import com.microsoft.kiota.store.BackingStoreFactory;
import com.microsoft.kiota.store.BackingStoreFactorySingleton;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** RequestAdapter implementation for OkHttp */
public class VertXRequestAdapter implements RequestAdapter {
    private static final String contentTypeHeaderKey = "Content-Type";
    @Nonnull private final WebClient client;
    @Nonnull private final AuthenticationProvider authProvider;
    @Nonnull private ParseNodeFactory pNodeFactory;
    @Nonnull private SerializationWriterFactory sWriterFactory;
    @Nonnull private String baseUrl = "";

    public void setBaseUrl(@Nonnull final String baseUrl) {
        this.baseUrl = Objects.requireNonNull(baseUrl);
    }

    @Nonnull public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Instantiates a new OkHttp request adapter with the provided authentication provider.
     * @param authenticationProvider the authentication provider to use for authenticating requests.
     */
    public VertXRequestAdapter(@Nonnull final AuthenticationProvider authenticationProvider) {
        this(authenticationProvider, null, null, null);
    }

    /**
     * Instantiates a new OkHttp request adapter with the provided authentication provider, and the parse node factory.
     * @param authenticationProvider the authentication provider to use for authenticating requests.
     * @param client the http client to use for sending requests.
     */
    public VertXRequestAdapter(
            @Nonnull final AuthenticationProvider authenticationProvider,
            @Nullable final WebClient client) {
        this(authenticationProvider, client, null, null);
    }

    /**
     * Instantiates a new OkHttp request adapter with the provided authentication provider, parse node factory, and the serialization writer factory.
     * @param authenticationProvider the authentication provider to use for authenticating requests.
     * @param client the http client to use for sending requests.
     * @param parseNodeFactory the parse node factory to use for parsing responses.
     */
    @SuppressWarnings("LambdaLast")
    public VertXRequestAdapter(
            @Nonnull final AuthenticationProvider authenticationProvider,
            @Nullable final WebClient client,
            @Nullable final ParseNodeFactory parseNodeFactory) {
        this(authenticationProvider, client, parseNodeFactory, null);
    }

    /**
     * Instantiates a new OkHttp request adapter with the provided authentication provider, parse node factory, serialization writer factory, and the http client.
     * @param authenticationProvider the authentication provider to use for authenticating requests.
     * @param client the http client to use for sending requests.
     * @param parseNodeFactory the parse node factory to use for parsing responses.
     * @param serializationWriterFactory the serialization writer factory to use for serializing requests.
     */
    @SuppressWarnings("LambdaLast")
    public VertXRequestAdapter(
            @Nonnull final AuthenticationProvider authenticationProvider,
            @Nullable final WebClient client,
            @Nullable final ParseNodeFactory parseNodeFactory,
            @Nullable final SerializationWriterFactory serializationWriterFactory) {
        this.authProvider =
                Objects.requireNonNull(
                        authenticationProvider, "parameter authenticationProvider cannot be null");
        if (client == null) {
            this.client = WebClient.create(Vertx.vertx());
        } else {
            this.client = client;
        }
        if (parseNodeFactory == null) {
            pNodeFactory = ParseNodeFactoryRegistry.defaultInstance;
        } else {
            pNodeFactory = parseNodeFactory;
        }

        if (serializationWriterFactory == null) {
            sWriterFactory = SerializationWriterFactoryRegistry.defaultInstance;
        } else {
            sWriterFactory = serializationWriterFactory;
        }
    }

    @Nonnull public SerializationWriterFactory getSerializationWriterFactory() {
        return sWriterFactory;
    }

    public void enableBackingStore(@Nullable final BackingStoreFactory backingStoreFactory) {
        this.pNodeFactory =
                Objects.requireNonNull(
                        ApiClientBuilder.enableBackingStoreForParseNodeFactory(pNodeFactory));
        this.sWriterFactory =
                Objects.requireNonNull(
                        ApiClientBuilder.enableBackingStoreForSerializationWriterFactory(
                                sWriterFactory));
        if (backingStoreFactory != null) {
            BackingStoreFactorySingleton.instance = backingStoreFactory;
        }
    }

    private static final String nullRequestInfoParameter = "parameter requestInfo cannot be null";
    private static final String nullForValueParameter = "parameter forValue cannot be null";
    private static final String nullFactoryParameter = "parameter factory cannot be null";

    @Nullable public <ModelType extends Parsable> List<ModelType> sendCollection(
            @Nonnull final RequestInformation requestInfo,
            @Nonnull final ParsableFactory<ModelType> factory,
            @Nullable final HashMap<String, ParsableFactory<? extends Parsable>> errorMappings) {
        Objects.requireNonNull(requestInfo, nullRequestInfoParameter);
        Objects.requireNonNull(factory, nullFactoryParameter);

            HttpResponse response = this.getHttpResponseMessage(requestInfo, null);
            final ResponseHandler responseHandler = getResponseHandler(requestInfo);
            if (responseHandler == null) {
                boolean closeResponse = true;
                try {
                    this.throwIfFailedResponse(response, errorMappings);
                    if (this.shouldReturnNull(response)) {
                        return null;
                    }
                    final ParseNode rootNode = getRootParseNode(response);
                    if (rootNode == null) {
                        closeResponse = false;
                        return null;
                    }
                        final List<ModelType> result =
                                rootNode.getCollectionOfObjectValues(factory);
                        return result;
                } finally {
                    closeResponse(closeResponse, response);
                }
            } else {
                return responseHandler.handleResponse(response, errorMappings);
            }
    }

    private ResponseHandler getResponseHandler(final RequestInformation requestInfo) {
        final Collection<RequestOption> requestOptions = requestInfo.getRequestOptions();
        for (final RequestOption rOption : requestOptions) {
            if (rOption instanceof ResponseHandlerOption) {
                final ResponseHandlerOption option = (ResponseHandlerOption) rOption;
                return option.getResponseHandler();
            }
        }
        return null;
    }

    private static final Pattern queryParametersCleanupPattern =
            Pattern.compile("\\{\\?[^\\}]+}", Pattern.CASE_INSENSITIVE);
    private final char[] queryParametersToDecodeForTracing = {'-', '.', '~', '$'};

    /** The key used for the event when a custom response handler is invoked. */
    @Nonnull public static final String eventResponseHandlerInvokedKey =
            "com.microsoft.kiota.response_handler_invoked";

    @Nullable public <ModelType extends Parsable> ModelType send(
            @Nonnull final RequestInformation requestInfo,
            @Nonnull final ParsableFactory<ModelType> factory,
            @Nullable final HashMap<String, ParsableFactory<? extends Parsable>> errorMappings) {
        Objects.requireNonNull(requestInfo, nullRequestInfoParameter);
        Objects.requireNonNull(factory, nullFactoryParameter);

            HttpResponse response = this.getHttpResponseMessage(requestInfo, null);
            final ResponseHandler responseHandler = getResponseHandler(requestInfo);
            if (responseHandler == null) {
                boolean closeResponse = true;
                try {
                    this.throwIfFailedResponse(response, errorMappings);
                    if (this.shouldReturnNull(response)) {
                        return null;
                    }
                    final ParseNode rootNode = getRootParseNode(response);
                    if (rootNode == null) {
                        closeResponse = false;
                        return null;
                    }
                    final ModelType result = rootNode.getObjectValue(factory);
                    return result;
                } finally {
                    closeResponse(closeResponse, response);
                }
            } else {
                return responseHandler.handleResponse(response, errorMappings);
            }
    }

    private void closeResponse(boolean closeResponse, HttpResponse response) {
        if (closeResponse && response.statusCode() != 204) {
            // response.close();
        }
    }

    @Nonnull private String getMediaTypeAndSubType(@Nonnull final MediaType mediaType) {
        return mediaType.type() + "/" + mediaType.subtype();
    }

    @Nullable public <ModelType> ModelType sendPrimitive(
            @Nonnull final RequestInformation requestInfo,
            @Nonnull final Class<ModelType> targetClass,
            @Nullable final HashMap<String, ParsableFactory<? extends Parsable>> errorMappings) {
        Objects.requireNonNull(requestInfo, nullRequestInfoParameter);
        Objects.requireNonNull(targetClass, nullForValueParameter);
            HttpResponse response = this.getHttpResponseMessage(requestInfo, null);
            final ResponseHandler responseHandler = getResponseHandler(requestInfo);
            if (responseHandler == null) {
                boolean closeResponse = true;
                try {
                    this.throwIfFailedResponse(response, errorMappings);
                    if (this.shouldReturnNull(response)) {
                        return null;
                    }
                    if (targetClass == Void.class) {
                        return null;
                    } else {
                        if (targetClass == InputStream.class) {
                            closeResponse = false;
                            final ResponseBody body = response.body();
                            if (body == null) {
                                return null;
                            }
                            final InputStream rawInputStream = body.byteStream();
                            return (ModelType) rawInputStream;
                        }
                        final ParseNode rootNode = getRootParseNode(response, span, span);
                        if (rootNode == null) {
                            closeResponse = false;
                            return null;
                        }
                            Object result;
                            if (targetClass == Boolean.class) {
                                result = rootNode.getBooleanValue();
                            } else if (targetClass == Byte.class) {
                                result = rootNode.getByteValue();
                            } else if (targetClass == String.class) {
                                result = rootNode.getStringValue();
                            } else if (targetClass == Short.class) {
                                result = rootNode.getShortValue();
                            } else if (targetClass == BigDecimal.class) {
                                result = rootNode.getBigDecimalValue();
                            } else if (targetClass == Double.class) {
                                result = rootNode.getDoubleValue();
                            } else if (targetClass == Integer.class) {
                                result = rootNode.getIntegerValue();
                            } else if (targetClass == Float.class) {
                                result = rootNode.getFloatValue();
                            } else if (targetClass == Long.class) {
                                result = rootNode.getLongValue();
                            } else if (targetClass == UUID.class) {
                                result = rootNode.getUUIDValue();
                            } else if (targetClass == OffsetDateTime.class) {
                                result = rootNode.getOffsetDateTimeValue();
                            } else if (targetClass == LocalDate.class) {
                                result = rootNode.getLocalDateValue();
                            } else if (targetClass == LocalTime.class) {
                                result = rootNode.getLocalTimeValue();
                            } else if (targetClass == PeriodAndDuration.class) {
                                result = rootNode.getPeriodAndDurationValue();
                            } else if (targetClass == byte[].class) {
                                result = rootNode.getByteArrayValue();
                            } else {
                                throw new RuntimeException(
                                        "unexpected payload type " + targetClass.getName());
                            }
                            return (ModelType) result;
                    }
                } finally {
                    closeResponse(closeResponse, response);
                }
            } else {
                return responseHandler.handleResponse(response, errorMappings);
            }
    }

    @Nullable public <ModelType extends Enum<ModelType>> ModelType sendEnum(
            @Nonnull final RequestInformation requestInfo,
            @Nonnull final Function<String, ModelType> forValue,
            @Nullable final HashMap<String, ParsableFactory<? extends Parsable>> errorMappings) {
        Objects.requireNonNull(requestInfo, nullRequestInfoParameter);
        Objects.requireNonNull(forValue, nullForValueParameter);
            HttpResponse response = this.getHttpResponseMessage(requestInfo, null);
            final ResponseHandler responseHandler = getResponseHandler(requestInfo);
            if (responseHandler == null) {
                boolean closeResponse = true;
                try {
                    this.throwIfFailedResponse(response, errorMappings);
                    if (this.shouldReturnNull(response)) {
                        return null;
                    }
                    final ParseNode rootNode = getRootParseNode(response);
                    if (rootNode == null) {
                        closeResponse = false;
                        return null;
                    }
                        final Object result = rootNode.getEnumValue(forValue);
                        return (ModelType) result;
                } finally {
                    closeResponse(closeResponse, response);
                }
            } else {
                return responseHandler.handleResponse(response, errorMappings);
            }
    }

    @Nullable public <ModelType extends Enum<ModelType>> List<ModelType> sendEnumCollection(
            @Nonnull final RequestInformation requestInfo,
            @Nonnull final Function<String, ModelType> forValue,
            @Nullable final HashMap<String, ParsableFactory<? extends Parsable>> errorMappings) {
        Objects.requireNonNull(requestInfo, nullRequestInfoParameter);
        Objects.requireNonNull(forValue, nullForValueParameter);
            HttpResponse response = this.getHttpResponseMessage(requestInfo, null);
            final ResponseHandler responseHandler = getResponseHandler(requestInfo);
            if (responseHandler == null) {
                boolean closeResponse = true;
                try {
                    this.throwIfFailedResponse(response, errorMappings);
                    if (this.shouldReturnNull(response)) {
                        return null;
                    }
                    final ParseNode rootNode = getRootParseNode(response);
                    if (rootNode == null) {
                        closeResponse = false;
                        return null;
                    }
                        final Object result = rootNode.getCollectionOfEnumValues(forValue);
                        return (List<ModelType>) result;
                } finally {
                    closeResponse(closeResponse, response);
                }
            } else {
                return responseHandler.handleResponse(response, errorMappings);
            }
    }

    @Nullable public <ModelType> List<ModelType> sendPrimitiveCollection(
            @Nonnull final RequestInformation requestInfo,
            @Nonnull final Class<ModelType> targetClass,
            @Nullable final HashMap<String, ParsableFactory<? extends Parsable>> errorMappings) {
        Objects.requireNonNull(requestInfo, nullRequestInfoParameter);

            HttpResponse response = getHttpResponseMessage(requestInfo, null);
            final ResponseHandler responseHandler = getResponseHandler(requestInfo);
            if (responseHandler == null) {
                boolean closeResponse = true;
                try {
                    this.throwIfFailedResponse(response, errorMappings);
                    if (this.shouldReturnNull(response)) {
                        return null;
                    }
                    final ParseNode rootNode = getRootParseNode(response);
                    if (rootNode == null) {
                        closeResponse = false;
                        return null;
                    }
                        final List<ModelType> result =
                                rootNode.getCollectionOfPrimitiveValues(targetClass);
                        return result;
                    } finally {
                        deserializationSpan.end();
                    }
            } else {
                return responseHandler.handleResponse(response, errorMappings);
            }
    }

    @Nullable private ParseNode getRootParseNode(final Response response) {
            final ResponseBody body =
                    response.body(); // closing the response closes the body and stream
            // https://square.github.io/okhttp/4.x/okhttp/okhttp3/-response-body/
            if (body == null) {
                return null;
            }
            final InputStream rawInputStream = body.byteStream();
            final MediaType contentType = body.contentType();
            if (contentType == null) {
                return null;
            }
            return pNodeFactory.getParseNode(getMediaTypeAndSubType(contentType), rawInputStream);
    }

    private boolean shouldReturnNull(final Response response) {
        final int statusCode = response.code();
        return statusCode == 204;
    }

    private HttpResponse throwIfFailedResponse(
            @Nonnull final HttpResponse response,
            @Nullable final HashMap<String, ParsableFactory<? extends Parsable>> errorMappings) {
            if (response.statusCode() >= 200 && response.statusCode() < 300) return response;

            final String statusCodeAsString = Integer.toString(response.statusCode());
            final int statusCode = response.statusCode();
            final ResponseHeaders responseHeaders =
                    HeadersCompatibility.getResponseHeaders(response.headers());
            if (errorMappings == null
                    || !errorMappings.containsKey(statusCodeAsString)
                            && !(statusCode >= 400
                                    && statusCode < 500
                                    && errorMappings.containsKey("4XX"))
                            && !(statusCode >= 500
                                    && statusCode < 600
                                    && errorMappings.containsKey("5XX"))) {
                final ApiException result =
                        new ApiExceptionBuilder()
                                .withMessage(
                                        "the server returned an unexpected status code and no error"
                                                + " class is registered for this code "
                                                + statusCode)
                                .withResponseStatusCode(statusCode)
                                .withResponseHeaders(responseHeaders)
                                .build();
                throw result;
            }

            final ParsableFactory<? extends Parsable> errorClass =
                    errorMappings.containsKey(statusCodeAsString)
                            ? errorMappings.get(statusCodeAsString)
                            : (statusCode >= 400 && statusCode < 500
                                    ? errorMappings.get("4XX")
                                    : errorMappings.get("5XX"));
            boolean closeResponse = true;
            try {
                final ParseNode rootNode = getRootParseNode(response);
                if (rootNode == null) {
                    closeResponse = false;
                    final ApiException result =
                            new ApiExceptionBuilder()
                                    .withMessage(
                                            "service returned status code"
                                                    + statusCode
                                                    + " but no response body was found")
                                    .withResponseStatusCode(statusCode)
                                    .withResponseHeaders(responseHeaders)
                                    .build();
                    throw result;
                }
                ApiException result =
                        new ApiExceptionBuilder(() -> rootNode.getObjectValue(errorClass))
                                .withResponseStatusCode(statusCode)
                                .withResponseHeaders(responseHeaders)
                                .build();
                    throw result;
            } finally {
                closeResponse(closeResponse, response);
            }
    }

    private static final String claimsKey = "claims";

    private HttpResponse getHttpResponseMessage(
            @Nonnull final RequestInformation requestInfo,
            @Nullable final String claims) {
        Objects.requireNonNull(requestInfo, nullRequestInfoParameter);
        this.setBaseUrlForRequestInformation(requestInfo);
        final Map<String, Object> additionalContext = new HashMap<String, Object>();
        if (claims != null && !claims.isEmpty()) {
            additionalContext.put(claimsKey, claims);
        }
        this.authProvider.authenticateRequest(requestInfo, additionalContext);
        final HttpResponse response =
                this.client
                        // TODO: stubbed
                        // getRequestFromRequestInformation(requestInfo))
                        .request(null, "")

                        .send()
                        .result();
        final String contentLengthHeaderValue = getHeaderValue(response, "Content-Length");
        if (contentLengthHeaderValue != null && !contentLengthHeaderValue.isEmpty()) {
            final int contentLengthHeaderValueAsInt =
                    Integer.parseInt(contentLengthHeaderValue);
        }
        final String contentTypeHeaderValue = getHeaderValue(response, "Content-Length");
        return this.retryCAEResponseIfRequired(
                response, requestInfo, claims);
    }

    private String getHeaderValue(final HttpResponse response, String key) {
        final List<String> headerValue = response.headers().getAll(key);
        if (headerValue != null && headerValue.size() > 0) {
            final String firstEntryValue = headerValue.get(0);
            if (firstEntryValue != null && !firstEntryValue.isEmpty()) {
                return firstEntryValue;
            }
        }
        return null;
    }

    private static final Pattern bearerPattern =
            Pattern.compile("^Bearer\\s.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern claimsPattern =
            Pattern.compile("\\s?claims=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    /** Key used for events when an authentication challenge is returned by the API */
    @Nonnull public static final String authenticateChallengedEventKey =
            "com.microsoft.kiota.authenticate_challenge_received";

    private HttpResponse retryCAEResponseIfRequired(
            @Nonnull final HttpResponse response,
            @Nonnull final RequestInformation requestInfo,
            @Nullable final String claims) {
            final String responseClaims = this.getClaimsFromResponse(response, requestInfo, claims);
            if (responseClaims != null && !responseClaims.isEmpty()) {
                if (requestInfo.content != null && requestInfo.content.markSupported()) {
                    try {
                        requestInfo.content.reset();
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                closeResponse(true, response);
                return this.getHttpResponseMessage(
                        requestInfo, responseClaims);
            }
            return response;
    }

    String getClaimsFromResponse(
            @Nonnull final HttpResponse response,
            @Nonnull final RequestInformation requestInfo,
            @Nullable final String claims) {
        if (response.statusCode() == 401
                && (claims == null || claims.isEmpty())
                && // we avoid infinite loops and retry only once
                (requestInfo.content == null || requestInfo.content.markSupported())) {
            final List<String> authenticateHeader = response.headers("WWW-Authenticate");
            if (!authenticateHeader.isEmpty()) {
                String rawHeaderValue = null;
                for (final String authenticateEntry : authenticateHeader) {
                    final Matcher matcher = bearerPattern.matcher(authenticateEntry);
                    if (matcher.matches()) {
                        rawHeaderValue = authenticateEntry.replaceFirst("^Bearer\\s", "");
                        break;
                    }
                }
                if (rawHeaderValue != null) {
                    final String[] parameters = rawHeaderValue.split(",");
                    for (final String parameter : parameters) {
                        final Matcher matcher = claimsPattern.matcher(parameter);
                        if (matcher.matches()) {
                            return matcher.group(1);
                        }
                    }
                }
            }
        }
        return null;
    }

    private void setBaseUrlForRequestInformation(@Nonnull final RequestInformation requestInfo) {
        Objects.requireNonNull(requestInfo);
        requestInfo.pathParameters.put("baseurl", getBaseUrl());
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Nonnull public <T> T convertToNativeRequest(@Nonnull final RequestInformation requestInfo) {
        try {
        Objects.requireNonNull(requestInfo, nullRequestInfoParameter);
            this.authProvider.authenticateRequest(requestInfo, null);
            return (T) getRequestFromRequestInformation(requestInfo);
        } catch (URISyntaxException | MalformedURLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Creates a new request from the request information instance.
     *
     * @param requestInfo       request information instance.
     * @return the created request instance.
     * @throws URISyntaxException if the URI is invalid.
     * @throws MalformedURLException if the URL is invalid.
     */
    protected @Nonnull Request getRequestFromRequestInformation(
            @Nonnull final RequestInformation requestInfo)
            throws URISyntaxException, MalformedURLException {
            final URL requestURL = requestInfo.getUri().toURL();

            RequestBody body =
                    requestInfo.content == null
                            ? null
                            : new RequestBody() {
                                @Override
                                public MediaType contentType() {
                                    final Set<String> contentTypes =
                                            requestInfo.headers.containsKey(contentTypeHeaderKey)
                                                    ? requestInfo.headers.get(contentTypeHeaderKey)
                                                    : new HashSet<>();
                                    if (contentTypes.isEmpty()) {
                                        return null;
                                    } else {
                                        final String contentType =
                                                contentTypes.toArray(new String[] {})[0];
                                        return MediaType.parse(contentType);
                                    }
                                }

                                @Override
                                public void writeTo(@Nonnull BufferedSink sink) throws IOException {
                                    sink.writeAll(Okio.source(requestInfo.content));
                                }
                            };

            // https://stackoverflow.com/a/35743536
            if (body == null
                    && (requestInfo.httpMethod.equals(HttpMethod.POST)
                            || requestInfo.httpMethod.equals(HttpMethod.PATCH)
                            || requestInfo.httpMethod.equals(HttpMethod.PUT))) {
                body = RequestBody.create(new byte[0]);
            }
            final Request.Builder requestBuilder =
                    new Request.Builder()
                            .url(requestURL)
                            .method(requestInfo.httpMethod.toString(), body);
            for (final Map.Entry<String, Set<String>> headerEntry :
                    requestInfo.headers.entrySet()) {
                for (final String headerValue : headerEntry.getValue()) {
                    requestBuilder.addHeader(headerEntry.getKey(), headerValue);
                }
            }
            final HttpRequest request = requestBuilder.build();
            final List<String> contentLengthHeader = request.headers().values("Content-Length");
            return request;
    }
}
