package io.swagger.client;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import retrofit2.Converter;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;

import play.libs.Json;
import play.libs.ws.WSClient;

import io.swagger.client.Play25CallAdapterFactory;
import io.swagger.client.Play25CallFactory;

import okhttp3.Interceptor;
import okhttp3.ResponseBody;
import io.swagger.client.auth.ApiKeyAuth;
import io.swagger.client.auth.Authentication;

/**
 * API client
 */
public class ApiClient {

    /** Underlying HTTP-client */
    private WSClient wsClient;

    /** Creates HTTP call instances */
    private Play25CallFactory callFactory;

    /** Create {@link java.util.concurrent.CompletionStage} instances from HTTP calls */
    private Play25CallAdapterFactory callAdapterFactory;

    /** Supported auths */
    private Map<String, Authentication> authentications;

    /** API base path */
    private String basePath = "http://petstore.swagger.io:80/v2";

    public ApiClient(WSClient wsClient) {
        this();
        this.wsClient = wsClient;
    }

    public ApiClient() {
        // Setup authentications (key: authentication name, value: authentication).
        authentications = new HashMap<>();
        authentications.put("api_key", new ApiKeyAuth("header", "api_key"));
        // authentications.put("http_basic_test", new HttpBasicAuth());
        // authentications.put("petstore_auth", new OAuth());
        // Prevent the authentications from being modified.
        authentications = Collections.unmodifiableMap(authentications);

    }

    /**
     * Creates a retrofit2 client for given API interface
     */
    public <S> S createService(Class<S> serviceClass) {
        if(!basePath.endsWith("/")) {
            basePath = basePath + "/";
        }

        Map<String, String> extraHeaders = new HashMap<>();
        List<Pair> extraQueryParams = new ArrayList<>();

        for (String authName : authentications.keySet()) {
            Authentication auth = authentications.get(authName);
            if (auth == null) throw new RuntimeException("Authentication undefined: " + authName);

            auth.applyToParams(extraQueryParams, extraHeaders);
        }

        if (callFactory == null) {
            callFactory = new Play25CallFactory(wsClient, extraHeaders, extraQueryParams);
        }
        if (callAdapterFactory == null) {
            callAdapterFactory = new Play25CallAdapterFactory();
        }

        return new Retrofit.Builder()
                        .baseUrl(basePath)
                        .addConverterFactory(new Converter.Factory() {

                            @Override
                            public Converter<ResponseBody, File> responseBodyConverter(Type type,
                                    Annotation[] annotations, Retrofit retrofit) {

                                if (!File.class.getTypeName().equals(type.getTypeName())) {
                                    return null;
                                }

                                return new Converter<ResponseBody, File>() {

                                    @Override
                                    public File convert(ResponseBody value) throws IOException {

                                        File file = File.createTempFile("retrofit-file", ".tmp");
                                        Files.write(Paths.get(file.getPath()), value.bytes());
                                        return file;
                                    }
                                };
                            }
                        })
                       .addConverterFactory(ScalarsConverterFactory.create())
                       .addConverterFactory(JacksonConverterFactory.create(Json.mapper()))
                       .callFactory(callFactory)
                       .addCallAdapterFactory(callAdapterFactory)
                       .build()
                       .create(serviceClass);
    }

    /**
     * Helper method to set API base path
     */
    public ApiClient setBasePath(String basePath) {
        this.basePath = basePath;
        return this;
    }

    /**
     * Get authentications (key: authentication name, value: authentication).
     */
    public Map<String, Authentication> getAuthentications() {
        return authentications;
    }

    /**
     * Get authentication for the given name.
     *
     * @param authName The authentication name
     * @return The authentication, null if not found
     */
    public Authentication getAuthentication(String authName) {
        return authentications.get(authName);
    }

    /**
     * Helper method to set API key value for the first API key authentication.
     */
    public ApiClient setApiKey(String apiKey) {
        for (Authentication auth : authentications.values()) {
            if (auth instanceof ApiKeyAuth) {
                ((ApiKeyAuth) auth).setApiKey(apiKey);
                return this;
            }
        }

        throw new RuntimeException("No API key authentication configured!");
    }

    /**
     * Helper method to set API key prefix for the first API key authentication.
     */
    public ApiClient setApiKeyPrefix(String apiKeyPrefix) {
        for (Authentication auth : authentications.values()) {
            if (auth instanceof ApiKeyAuth) {
                ((ApiKeyAuth) auth).setApiKeyPrefix(apiKeyPrefix);
                return this;
            }
        }

        throw new RuntimeException("No API key authentication configured!");
    }

    /**
     * Helper method to set HTTP call instances factory
     */
    public ApiClient setCallFactory(Play25CallFactory callFactory) {
        this.callFactory = callFactory;
        return this;
    }

    /**
     * Helper method to set {@link java.util.concurrent.CompletionStage} instances factory
     */
    public ApiClient setCallAdapterFactory(Play25CallAdapterFactory callAdapterFactory) {
        this.callAdapterFactory = callAdapterFactory;
        return this;
    }

}


