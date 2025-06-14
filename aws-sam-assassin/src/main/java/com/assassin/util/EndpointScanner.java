package com.assassin.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Scans handler files to extract API endpoint information
 */
public class EndpointScanner {
    
    private static final String HANDLERS_PATH = "src/main/java/com/assassin/handlers";
    private static final Pattern HTTP_METHOD_PATTERN = Pattern.compile("\"(GET|POST|PUT|DELETE|PATCH)\"\\s*\\.equals\\s*\\(\\s*httpMethod\\s*\\)");
    private static final Pattern PATH_PATTERN = Pattern.compile("\"(/[^\"]+)\"\\s*\\.equals\\s*\\(\\s*path\\s*\\)");
    private static final Pattern PATH_REGEX_PATTERN = Pattern.compile("Pattern\\.compile\\s*\\(\\s*\"([^\"]+)\"");
    private static final Pattern ROUTE_PATTERN = Pattern.compile("if\\s*\\(([^)]+)\\)\\s*\\{([^}]+)return\\s+(\\w+)\\s*\\(");
    
    public static class Endpoint {
        public String method;
        public String path;
        public String handler;
        public String handlerMethod;
        public boolean authenticated = true;
        public List<String> pathParams = new ArrayList<>();
        
        @Override
        public String toString() {
            return String.format("%s %s -> %s.%s", method, path, handler, handlerMethod);
        }
    }
    
    public static void main(String[] args) throws IOException {
        EndpointScanner scanner = new EndpointScanner();
        List<Endpoint> endpoints = scanner.scanAllHandlers();
        
        System.out.println("Found " + endpoints.size() + " endpoints:\n");
        
        // Group by handler
        Map<String, List<Endpoint>> byHandler = endpoints.stream()
            .collect(Collectors.groupingBy(e -> e.handler));
        
        byHandler.forEach((handler, handlerEndpoints) -> {
            System.out.println("\n" + handler + ":");
            handlerEndpoints.forEach(e -> {
                System.out.println("  " + e.method + " " + e.path);
                if (!e.pathParams.isEmpty()) {
                    System.out.println("    Path params: " + e.pathParams);
                }
            });
        });
        
        // Generate OpenAPI paths
        System.out.println("\n\nOpenAPI Paths Structure:");
        System.out.println("========================");
        generateOpenApiPaths(endpoints);
    }
    
    public List<Endpoint> scanAllHandlers() throws IOException {
        List<Endpoint> allEndpoints = new ArrayList<>();
        
        File handlersDir = new File(HANDLERS_PATH);
        if (!handlersDir.exists()) {
            System.err.println("Handlers directory not found: " + handlersDir.getAbsolutePath());
            return allEndpoints;
        }
        
        Files.walk(handlersDir.toPath())
            .filter(path -> path.toString().endsWith(".java"))
            .filter(path -> !path.toString().contains("Test"))
            .forEach(path -> {
                try {
                    List<Endpoint> endpoints = scanHandler(path);
                    allEndpoints.addAll(endpoints);
                } catch (IOException e) {
                    System.err.println("Error scanning " + path + ": " + e.getMessage());
                }
            });
        
        return allEndpoints;
    }
    
    private List<Endpoint> scanHandler(Path handlerPath) throws IOException {
        List<Endpoint> endpoints = new ArrayList<>();
        String content = Files.readString(handlerPath);
        String handlerName = handlerPath.getFileName().toString().replace(".java", "");
        
        // Extract routing logic
        List<String> lines = Files.readAllLines(handlerPath);
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            
            // Look for routing conditions
            if (line.contains("if") && (line.contains("httpMethod") || line.contains("path"))) {
                Endpoint endpoint = extractEndpoint(lines, i, handlerName);
                if (endpoint != null) {
                    endpoints.add(endpoint);
                }
            }
        }
        
        return endpoints;
    }
    
    private Endpoint extractEndpoint(List<String> lines, int startIndex, String handlerName) {
        Endpoint endpoint = new Endpoint();
        endpoint.handler = handlerName;
        
        // Build the complete if statement
        StringBuilder condition = new StringBuilder();
        int braceCount = 0;
        boolean inCondition = false;
        
        for (int i = startIndex; i < Math.min(startIndex + 20, lines.size()); i++) {
            String line = lines.get(i);
            
            if (line.contains("if") && !inCondition) {
                inCondition = true;
            }
            
            if (inCondition) {
                condition.append(line).append(" ");
                
                // Count braces to find the return statement
                for (char c : line.toCharArray()) {
                    if (c == '{') braceCount++;
                    if (c == '}') braceCount--;
                }
                
                // Look for return statement
                if (line.contains("return") && line.contains("(")) {
                    Pattern returnPattern = Pattern.compile("return\\s+(\\w+)\\s*\\(");
                    Matcher m = returnPattern.matcher(line);
                    if (m.find()) {
                        endpoint.handlerMethod = m.group(1);
                    }
                }
                
                if (braceCount == 0 && line.contains("}")) {
                    break;
                }
            }
        }
        
        String fullCondition = condition.toString();
        
        // Extract HTTP method
        Matcher methodMatcher = HTTP_METHOD_PATTERN.matcher(fullCondition);
        if (methodMatcher.find()) {
            endpoint.method = methodMatcher.group(1);
        }
        
        // Extract path
        Matcher pathMatcher = PATH_PATTERN.matcher(fullCondition);
        if (pathMatcher.find()) {
            endpoint.path = pathMatcher.group(1);
        } else {
            // Try to extract from regex pattern
            Matcher regexMatcher = PATH_REGEX_PATTERN.matcher(fullCondition);
            if (regexMatcher.find()) {
                String regex = regexMatcher.group(1);
                endpoint.path = convertRegexToPath(regex);
                endpoint.pathParams = extractPathParams(regex);
            }
        }
        
        // Only return if we found both method and path
        if (endpoint.method != null && endpoint.path != null && endpoint.handlerMethod != null) {
            return endpoint;
        }
        
        return null;
    }
    
    private String convertRegexToPath(String regex) {
        // Convert regex patterns to OpenAPI path format
        String path = regex;
        
        // Replace regex groups with parameter placeholders
        path = path.replaceAll("\\([^)]+\\)", "{param}");
        path = path.replaceAll("\\\\", "");
        path = path.replaceAll("\\$", "");
        path = path.replaceAll("\\^", "");
        
        // Try to guess parameter names from context
        if (path.contains("/games/{param}")) {
            path = path.replaceFirst("\\{param\\}", "{gameId}");
        }
        if (path.contains("/players/{param}")) {
            path = path.replaceFirst("\\{param\\}", "{playerId}");
        }
        if (path.contains("/eliminations/{param}")) {
            path = path.replaceFirst("\\{param\\}", "{eliminationId}");
        }
        if (path.contains("/safezones/{param}")) {
            path = path.replaceFirst("\\{param\\}", "{safeZoneId}");
        }
        
        return path;
    }
    
    private List<String> extractPathParams(String regex) {
        List<String> params = new ArrayList<>();
        
        if (regex.contains("games")) params.add("gameId");
        if (regex.contains("players")) params.add("playerId");
        if (regex.contains("eliminations")) params.add("eliminationId");
        if (regex.contains("safezones")) params.add("safeZoneId");
        
        return params;
    }
    
    private static void generateOpenApiPaths(List<Endpoint> endpoints) {
        // Group by path
        Map<String, List<Endpoint>> byPath = endpoints.stream()
            .collect(Collectors.groupingBy(e -> e.path));
        
        System.out.println("paths:");
        
        byPath.forEach((path, pathEndpoints) -> {
            System.out.println("  " + path + ":");
            
            pathEndpoints.forEach(endpoint -> {
                System.out.println("    " + endpoint.method.toLowerCase() + ":");
                System.out.println("      summary: " + generateSummary(endpoint));
                System.out.println("      tags:");
                System.out.println("        - " + getTag(endpoint.handler));
                System.out.println("      operationId: " + endpoint.handlerMethod);
                
                if (!endpoint.pathParams.isEmpty()) {
                    System.out.println("      parameters:");
                    endpoint.pathParams.forEach(param -> {
                        System.out.println("        - name: " + param);
                        System.out.println("          in: path");
                        System.out.println("          required: true");
                        System.out.println("          schema:");
                        System.out.println("            type: string");
                    });
                }
                
                System.out.println("      responses:");
                System.out.println("        '200':");
                System.out.println("          description: Successful response");
                System.out.println("        '401':");
                System.out.println("          description: Unauthorized");
                System.out.println("        '404':");
                System.out.println("          description: Not found");
                System.out.println();
            });
        });
    }
    
    private static String generateSummary(Endpoint endpoint) {
        String method = endpoint.method;
        String handler = endpoint.handlerMethod;
        
        // Convert handler method name to readable summary
        String summary = handler
            .replaceAll("([A-Z])", " $1")
            .trim()
            .substring(0, 1).toUpperCase() + 
            handler.replaceAll("([A-Z])", " $1").trim().substring(1);
        
        return summary;
    }
    
    private static String getTag(String handler) {
        // Map handler names to tags
        if (handler.contains("Game")) return "Games";
        if (handler.contains("Player")) return "Players";
        if (handler.contains("Kill") || handler.contains("Elimination")) return "Eliminations";
        if (handler.contains("Location")) return "Location";
        if (handler.contains("SafeZone")) return "Safe Zones";
        if (handler.contains("Map")) return "Map";
        if (handler.contains("Payment")) return "Payments";
        if (handler.contains("Subscription")) return "Subscriptions";
        if (handler.contains("Privacy")) return "Privacy";
        if (handler.contains("Security")) return "Security";
        if (handler.contains("Admin")) return "Admin";
        if (handler.contains("Notification")) return "Notifications";
        if (handler.contains("Statistics")) return "Statistics";
        if (handler.contains("DataExport")) return "Data Export";
        
        return "Other";
    }
}