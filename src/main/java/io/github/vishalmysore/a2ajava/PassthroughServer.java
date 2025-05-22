package io.github.vishalmysore.a2ajava;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PassthroughServer {
    /**
     * This is for testing purposes only.
     */
    private static String username = null;
    private static String password = null;
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        String targetUrl = args.length > 0 ? args[0] : "http://localhost:8080/invoke";
        if (args.length > 2) {
            username = args[1];
            password = args[2];
            System.err.println("🔑 Authentication credentials provided");
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line;

        System.err.println("🚀 Java passthrough server running for: " + targetUrl);

        while ((line = reader.readLine()) != null) {
            if (!line.trim().isEmpty()) {
                try {
                    System.err.println("⬅️ Incoming: " + line);
                    String response = sendPostRequest(targetUrl, line);

                    if (response.trim().isEmpty()) {
                        System.err.println("⚠️ Empty response from backend (likely a notification).");
                        continue;
                    }

                    // Parse + re-serialize to normalize the JSON
                    try {
                        JsonNode json = mapper.readTree(response);
                        String cleanJson = mapper.writeValueAsString(json);
                        System.err.println("➡️ Response: " + cleanJson);
                        System.out.println(cleanJson);  // newline-delimited
                        System.out.flush();
                    } catch (Exception e) {
                        System.err.println("❌ Invalid JSON from backend: " + e.getMessage());
                        String err = "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700,\"message\":\"" +
                                e.getMessage().replace("\"", "'") + "\"}}";
                        System.out.println(err);
                        System.out.flush();
                    }

                } catch (Exception e) {
                    System.err.println("❌ Error sending request: " + e.getMessage());
                    String err = "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"" +
                            e.getMessage().replace("\"", "'") + "\"}}";
                    System.out.println(err);
                    System.out.flush();
                }
            }
        }
    }

    public static String sendPostRequest(String urlStr, String jsonPayload) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        con.setRequestProperty("Connection", "keep-alive");
        if (username != null && password != null) {
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            con.setRequestProperty("Authorization", "Basic " + encodedAuth);
        }
        con.setDoOutput(true);

        try (OutputStream os = con.getOutputStream()) {
            byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
            os.write(input);
        }

        int status = con.getResponseCode();
        InputStream is = status >= 200 && status < 300 ? con.getInputStream() : con.getErrorStream();

        try (BufferedReader in = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder response = new StringBuilder();
            String respLine;
            while ((respLine = in.readLine()) != null) {
                response.append(respLine);
            }
            return response.toString();
        } finally {
            con.disconnect(); // HttpURLConnection keeps connections in the pool
        }
    }
}
