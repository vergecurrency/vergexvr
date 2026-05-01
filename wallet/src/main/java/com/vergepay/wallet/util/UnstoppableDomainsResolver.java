package com.vergepay.wallet.util;

import android.content.Context;
import android.text.TextUtils;

import com.vergepay.wallet.BuildConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;

import javax.annotation.Nullable;

public final class UnstoppableDomainsResolver {
    private static final String API_BASE_URL = "https://api.unstoppabledomains.com/resolve/domains/";

    private UnstoppableDomainsResolver() { }

    public static boolean looksLikeDomain(String input) {
        if (TextUtils.isEmpty(input)) return false;

        String candidate = input.trim().toLowerCase();
        return candidate.indexOf('.') > 0
                && candidate.indexOf(' ') < 0
                && !candidate.contains("://")
                && !candidate.contains("/");
    }

    @Nullable
    public static String resolveAddress(Context context, String domain, String ticker)
            throws IOException, ResolutionException {
        String token = BuildConfig.UNSTOPPABLE_DOMAINS_API_TOKEN;
        if (TextUtils.isEmpty(token)) {
            throw new ResolutionException("missing_api_token");
        }

        String url = API_BASE_URL + URLEncoder.encode(domain.trim(), "UTF-8");
        com.squareup.okhttp.Request request = NetworkUtils.getBrowserRequestBuilder(url)
                .header("Authorization", "Bearer " + token)
                .build();

        com.squareup.okhttp.Response response =
                NetworkUtils.getHttpClient(context).newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new ResolutionException("http_" + response.code());
        }

        String responseBody = response.body() != null ? response.body().string() : null;
        if (TextUtils.isEmpty(responseBody)) {
            throw new ResolutionException("empty_response");
        }

        try {
            JSONObject json = new JSONObject(responseBody);
            JSONObject records = json.optJSONObject("records");
            if (records == null) {
                throw new ResolutionException("no_records");
            }

            String recordKey = "crypto." + ticker.toUpperCase() + ".address";
            String address = records.optString(recordKey, null);
            if (TextUtils.isEmpty(address)) {
                throw new ResolutionException("record_not_found");
            }

            return address.trim();
        } catch (JSONException e) {
            throw new ResolutionException("invalid_json", e);
        }
    }

    public static final class ResolutionException extends Exception {
        public ResolutionException(String message) {
            super(message);
        }

        public ResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
