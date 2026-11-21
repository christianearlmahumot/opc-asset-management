package edu.citu.opc.fams.common.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * SRS 3.1.2 / SDD 1.1 FileStorageService: files (asset photos, scanned documents, generated PDFs)
 * go to a PRIVATE Supabase Storage bucket through its REST API. The key stays on the server; the
 * browser never talks to Supabase (SRS 3.1.3). Enabled with fams.storage.type=supabase.
 */
@Component
@ConditionalOnProperty(name = "fams.storage.type", havingValue = "supabase")
public class SupabaseFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(SupabaseFileStorage.class);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String base;
    private final String key;
    private final String bucket;

    public SupabaseFileStorage(@Value("${fams.storage.supabase.url:}") String url,
                               @Value("${fams.storage.supabase.service-key:}") String key,
                               @Value("${fams.storage.supabase.bucket:fams-files}") String bucket) {
        url = clean(url);
        key = clean(key);
        bucket = clean(bucket);
        if (url.isEmpty() || key.isEmpty() || bucket.isEmpty()) {
            throw new IllegalStateException(
                    "STORAGE_TYPE=supabase needs SUPABASE_URL, SUPABASE_SERVICE_KEY and SUPABASE_BUCKET.");
        }
        if (!url.startsWith("https://")) {
            throw new IllegalStateException("SUPABASE_URL must start with https:// (e.g. https://<ref>.supabase.co).");
        }
        this.base = url.replaceAll("/+$", "") + "/storage/v1";
        this.key = key;
        this.bucket = bucket;
        ensurePrivateBucket();
    }

    @Override
    public String save(String path, byte[] content, String contentType) throws IOException {
        HttpRequest req = request("/object/" + bucket + "/" + encode(path))
                .header("Content-Type", contentType != null ? contentType : "application/octet-stream")
                .header("x-upsert", "true")
                .POST(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();
        HttpResponse<String> res = send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IOException("Supabase Storage upload failed (" + res.statusCode() + "): " + res.body());
        }
        return path;
    }

    @Override
    public byte[] load(String path) throws IOException {
        HttpRequest req = request("/object/" + bucket + "/" + encode(path)).GET().build();
        HttpResponse<byte[]> res = send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() / 100 != 2) {
            throw new IOException("Supabase Storage download failed (" + res.statusCode() + ") for " + path);
        }
        return res.body();
    }

    @Override
    public void delete(String path) throws IOException {
        HttpRequest req = request("/object/" + bucket + "/" + encode(path)).DELETE().build();
        HttpResponse<String> res = send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2 && res.statusCode() != 404) {
            throw new IOException("Supabase Storage delete failed (" + res.statusCode() + "): " + res.body());
        }
    }

    /** Creates the bucket as private if it does not exist, and fails fast on a wrong URL or key. */
    private void ensurePrivateBucket() {
        try {
            HttpResponse<String> get = send(request("/bucket/" + bucket).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (get.statusCode() == 200) {
                if (get.body().replace(" ", "").contains("\"public\":true")) {
                    log.warn("Supabase bucket '{}' is PUBLIC. Make it private: it holds personal data (SRS 2.4).", bucket);
                }
                log.info("Using Supabase Storage bucket '{}'.", bucket);
                return;
            }
            if (get.statusCode() == 401 || get.statusCode() == 403) {
                throw new IllegalStateException("Supabase Storage refused the key (" + get.statusCode()
                        + "). Check SUPABASE_SERVICE_KEY: it must be the secret/service_role key.");
            }
            String body = "{\"id\":\"" + bucket + "\",\"name\":\"" + bucket + "\",\"public\":false}";
            HttpResponse<String> post = send(request("/bucket")
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (post.statusCode() / 100 != 2) {
                throw new IllegalStateException("Could not create Supabase bucket '" + bucket + "' ("
                        + post.statusCode() + "): " + post.body());
            }
            log.info("Created private Supabase Storage bucket '{}'.", bucket);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot reach Supabase Storage at " + base + ". Check SUPABASE_URL.", e);
        }
    }

    /**
     * New Supabase secret keys (sb_secret_...) go in the apikey header only; the older service_role
     * key is a JWT and is also sent as the bearer token.
     */
    private HttpRequest.Builder request(String pathAndQuery) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + pathAndQuery))
                .timeout(Duration.ofSeconds(60))
                .header("apikey", key);
        if (key.startsWith("eyJ")) b.header("Authorization", "Bearer " + key);
        return b;
    }

    private <T> HttpResponse<T> send(HttpRequest req, HttpResponse.BodyHandler<T> handler) throws IOException {
        try {
            return http.send(req, handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while calling Supabase Storage", e);
        }
    }

    /**
     * Removes every space and invisible character (newline, non-breaking space, zero-width space,
     * byte-order mark) that copy-paste into a dashboard can add. None of them is valid in a URL,
     * key or bucket name.
     */
    static String clean(String s) {
        return s == null ? "" : s.replaceAll("[\\s\\p{Z}\\p{Cf}\\p{Cc}]", "");
    }

    /** Encodes each path segment but keeps the slashes, e.g. assets/12/photo-ab.jpg. */
    private static String encode(String path) {
        if (path.contains("..")) throw new IllegalArgumentException("Invalid storage path");
        return Arrays.stream(path.replace('\\', '/').split("/"))
                .filter(s -> !s.isEmpty())
                .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
    }
}
