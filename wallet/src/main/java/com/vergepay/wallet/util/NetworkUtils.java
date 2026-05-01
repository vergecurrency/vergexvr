package com.vergepay.wallet.util;

import android.content.Context;

import com.vergepay.wallet.Constants;
import com.squareup.okhttp.Cache;
import com.squareup.okhttp.ConnectionSpec;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;

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
            httpClient = new OkHttpClient();
            httpClient.setProxy(Constants.TOR_LOCAL_PROXY);
            httpClient.setConnectionSpecs(Collections.singletonList(ConnectionSpec.MODERN_TLS));
            httpClient.setConnectTimeout(Constants.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            // Setup cache
            File cacheDir = new File(context.getCacheDir(), Constants.HTTP_CACHE_NAME);
            Cache cache = new Cache(cacheDir, Constants.HTTP_CACHE_SIZE);
            httpClient.setCache(cache);
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
