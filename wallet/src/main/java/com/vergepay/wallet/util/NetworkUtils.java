package com.vergepay.wallet.util;

import android.content.Context;

import com.vergepay.wallet.Constants;
import okhttp3.Cache;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author John L. Jegutanis
 */
public class NetworkUtils {
    public static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36";
    private static OkHttpClient httpClient;

    public static OkHttpClient getHttpClient(Context context) {
        if (httpClient == null) {
            // Setup cache
            File cacheDir = new File(context.getCacheDir(), Constants.HTTP_CACHE_NAME);
            Cache cache = new Cache(cacheDir, Constants.HTTP_CACHE_SIZE);
            httpClient = new OkHttpClient.Builder()
                    .proxy(Constants.TOR_LOCAL_PROXY)
                    .connectionSpecs(Collections.singletonList(ConnectionSpec.MODERN_TLS))
                    .connectTimeout(Constants.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .cache(cache)
                    .build();
        }
        return httpClient;
    }

    public static Request.Builder getBrowserRequestBuilder(String url) {
        return new Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "application/json,text/plain,*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control", "no-cache");
    }
}
