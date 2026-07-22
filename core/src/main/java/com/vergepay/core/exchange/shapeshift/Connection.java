package com.vergepay.core.exchange.shapeshift;

import okhttp3.Cache;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;

import java.io.File;
import java.util.Collections;

/**
 * @author John L. Jegutanis
 */
abstract public class Connection {
    private static final String DEFAULT_BASE_URL = "https://shapeshift.io/";

    OkHttpClient client;
    String baseUrl = DEFAULT_BASE_URL;

    protected Connection(OkHttpClient client) {
        this.client = client;
    }

    protected Connection() {
        client = new OkHttpClient.Builder()
                .connectionSpecs(Collections.singletonList(ConnectionSpec.MODERN_TLS))
                .build();
    }

    /**
     * Setup caching. The cache directory should be private, and untrusted applications should not
     * be able to read its contents!
     */
    public void setCache(File cacheDirectory) {
        int cacheSize = 256 * 1024; // 256 KiB
        Cache cache = new Cache(cacheDirectory, cacheSize);
        client = client.newBuilder().cache(cache).build();
    }

    public boolean isCacheSet() {
        return client.cache() != null;
    }

    protected String getApiUrl(String path) {
        return baseUrl + path;
    }
}
