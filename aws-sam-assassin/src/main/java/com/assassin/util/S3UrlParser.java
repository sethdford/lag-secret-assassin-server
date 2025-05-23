package com.assassin.util;

import java.net.URI;
import java.net.URISyntaxException;

public class S3UrlParser {

    public static class S3Path {
        private final String bucket;
        private final String key;

        public S3Path(String bucket, String key) {
            this.bucket = bucket;
            this.key = key;
        }

        public String getBucket() {
            return bucket;
        }

        public String getKey() {
            return key;
        }
    }

    /**
     * Parses an S3 URL (e.g., s3://bucket-name/path/to/object.jpg or https://bucket-name.s3.region.amazonaws.com/path/to/object.jpg)
     * and returns the bucket and key.
     *
     * @param s3Url The S3 URL to parse.
     * @return An S3Path object containing the bucket and key.
     * @throws IllegalArgumentException if the URL is not a valid S3 URL.
     */
    public static S3Path parseS3Url(String s3Url) throws IllegalArgumentException {
        if (s3Url == null || s3Url.trim().isEmpty()) {
            throw new IllegalArgumentException("S3 URL cannot be null or empty.");
        }

        try {
            URI uri = new URI(s3Url);
            String scheme = uri.getScheme();
            String authority = uri.getAuthority();
            String path = uri.getPath();

            if ("s3".equalsIgnoreCase(scheme)) {
                if (authority == null || authority.isEmpty()) {
                    throw new IllegalArgumentException("S3 URL (s3://...) must have a bucket name (authority).");
                }
                String bucket = authority;
                String key = (path != null && path.startsWith("/")) ? path.substring(1) : path;
                if (key == null || key.isEmpty()) {
                    throw new IllegalArgumentException("S3 URL (s3://...) must have a key (path).");
                }
                return new S3Path(bucket, key);
            } else if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
                // Attempt to parse https://bucket.s3... or https://s3.region.bucket... formats
                if (authority == null || !authority.contains(".s3")) { // Basic check
                    throw new IllegalArgumentException("HTTP(S) URL does not appear to be a valid S3 path-style or virtual-hosted-style URL.");
                }
                
                String bucket = null;
                String key = null;

                // Virtual-hosted style: bucket.s3.region.amazonaws.com/key or bucket.s3.amazonaws.com/key
                if (authority.matches("^[^.]+\\.s3(\\.[^.]+)?\\.amazonaws\\.com$")) {
                    bucket = authority.substring(0, authority.indexOf(".s3"));
                } 
                // Path-style: s3.region.amazonaws.com/bucket/key or s3.amazonaws.com/bucket/key
                else if (authority.matches("^s3(\\.[^.]+)?\\.amazonaws\\.com$")) {
                    if (path != null && path.length() > 1) {
                        String[] pathParts = path.substring(1).split("/", 2);
                        bucket = pathParts[0];
                        if (pathParts.length > 1) {
                            key = pathParts[1];
                        }
                    }
                } else {
                     throw new IllegalArgumentException("Unsupported S3 HTTP(S) URL format: " + s3Url);
                }
                
                if (bucket == null || bucket.isEmpty()) {
                    throw new IllegalArgumentException("Could not extract bucket name from S3 HTTP(S) URL: " + s3Url);
                }
                if (key == null || key.isEmpty()) {
                     // For virtual-hosted, the key is the path (minus leading /)
                     if (path != null && path.startsWith("/")) {
                        key = path.substring(1);
                        if (key.isEmpty()) { // e.g. https://bucket.s3.amazonaws.com/ (key should not be empty)
                             throw new IllegalArgumentException("Extracted key is empty from S3 HTTP(S) URL path: " + s3Url);
                        }
                     } else {
                        throw new IllegalArgumentException("Could not extract key from S3 HTTP(S) URL path: " + s3Url);
                     }
                }
                return new S3Path(bucket, key);
            } else {
                throw new IllegalArgumentException("Unsupported S3 URL scheme: " + scheme + ". Must be 's3', 'http', or 'https'.");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL syntax: " + s3Url, e);
        }
    }
} 