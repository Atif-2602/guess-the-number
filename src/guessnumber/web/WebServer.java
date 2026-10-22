package guessnumber.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import guessnumber.Game;
import guessnumber.GameSession;
import guessnumber.GuessResult;

/**
 * Small web server for the "Guess the Number" mini project.
 *
 * <p>It uses only the JDK ({@code com.sun.net.httpserver}), so there are no
 * external libraries to download. The game rules stay in Java; the browser
 * only paints the screen and calls two JSON endpoints.</p>
 *
 * <pre>
 *   POST /api/new-game          starts a round, returns a game id
 *   POST /api/guess             sends one guess, returns a hint
 *   GET  /                      serves the web folder
 * </pre>
 */
public final class WebServer {

    private static final int DEFAULT_PORT = 8080;
    private static final int MAX_SESSIONS = 10_000;

    private static final Map<String, GameSession> SESSIONS = new ConcurrentHashMap<>();
    private static Path webRoot;

    private WebServer() {
        // utility class
    }

    public static void main(String[] args) throws IOException {
        int port = parsePort(args);
        webRoot = locateWebRoot();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/new-game", WebServer::handleNewGame);
        server.createContext("/api/guess", WebServer::handleGuess);
        server.createContext("/", WebServer::handleStaticFile);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        String url = "http://localhost:" + port + "/";
        System.out.println();
        System.out.println("================================================================");
        System.out.println("   GUESS THE NUMBER - web version is running");
        System.out.println("   Open:  " + url);
        System.out.println("   Stop:  press Ctrl+C in this window");
        System.out.println("================================================================");
        System.out.println();

        openBrowser(url);
    }

    private static int parsePort(String[] args) {
        if (args.length == 0) {
            return DEFAULT_PORT;
        }
        try {
            int port = Integer.parseInt(args[0]);
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
            return port;
        } catch (NumberFormatException e) {
            System.err.println("Invalid port '" + args[0] + "'. Using " + DEFAULT_PORT + ".");
            return DEFAULT_PORT;
        }
    }

    /**
     * Finds the web folder whether the program is started from the project
     * root or from a sub folder.
     */
    private static Path locateWebRoot() throws IOException {
        Path current = Paths.get("").toAbsolutePath();
        Path candidate = current;

        for (int depth = 0; depth < 4; depth++) {
            Path web = candidate.resolve("web");
            if (Files.isDirectory(web)) {
                return web;
            }
            candidate = candidate.getParent();
            if (candidate == null) {
                break;
            }
        }
        throw new IOException("Could not find the 'web' folder. "
                + "Run the server from the project root folder.");
    }

    private static void openBrowser(String url) {
        // -Dgtn.noBrowser=true or GTN_NO_BROWSER=true keeps automatic tests quiet.
        if (Boolean.getBoolean("gtn.noBrowser") || "true".equalsIgnoreCase(System.getenv("GTN_NO_BROWSER"))) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception e) {
            System.out.println("Open " + url + " in your browser manually.");
        }
    }

    // ------------------------------------------------------------------
    // API: new game
    // ------------------------------------------------------------------

    private static void handleNewGame(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Use POST for this endpoint\"}");
            return;
        }

        try {
            forgetOldSessions();

            GameSession session = new GameSession();
            SESSIONS.put(session.getId(), session);

            sendJson(exchange, 200, "{"
                    + "\"gameId\":\"" + session.getId() + "\","
                    + "\"min\":" + Game.MIN_NUMBER + ","
                    + "\"max\":" + Game.MAX_NUMBER + ","
                    + "\"attempts\":0,"
                    + "\"finished\":false"
                    + "}");
        } catch (RuntimeException e) {
            sendJson(exchange, 500, "{\"error\":\"Could not start a game\"}");
        }
    }

    // ------------------------------------------------------------------
    // API: guess
    // ------------------------------------------------------------------

    private static void handleGuess(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Use POST for this endpoint\"}");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String gameId = readStringField(body, "gameId");
        Integer guess = readIntField(body, "guess");

        if (gameId == null) {
            sendJson(exchange, 400, invalid("Missing gameId. Start a new game first."));
            return;
        }

        GameSession session = SESSIONS.get(gameId);
        if (session == null) {
            sendJson(exchange, 404, "{\"status\":\"GAME_NOT_FOUND\","
                    + "\"message\":\"Game session not found. Start a new game.\"}");
            return;
        }

        if (session.isFinished()) {
            sendJson(exchange, 409, "{\"status\":\"ALREADY_FINISHED\","
                    + "\"message\":\"This round is over. Start a new game.\"}");
            return;
        }

        if (guess == null) {
            sendJson(exchange, 400, invalid("Please send a whole number."));
            return;
        }

        // Out-of-range guesses are rejected and do not count as an attempt.
        if (!Game.isInRange(guess)) {
            sendJson(exchange, 400, invalid("Out of range. Enter a number between "
                    + Game.MIN_NUMBER + " and " + Game.MAX_NUMBER + "."));
            return;
        }

        GuessResult result;
        try {
            result = session.submitGuess(guess);
        } catch (IllegalStateException e) {
            sendJson(exchange, 409, "{\"status\":\"ALREADY_FINISHED\","
                    + "\"message\":\"This round is over. Start a new game.\"}");
            return;
        }

        StringBuilder json = new StringBuilder("{");
        json.append("\"status\":\"").append(result.name()).append("\",");
        json.append("\"message\":\"").append(escapeJson(result.getHint())).append("\",");
        json.append("\"attempts\":").append(session.getAttempts()).append(',');
        json.append("\"finished\":").append(session.isFinished()).append(',');
        json.append("\"min\":").append(Game.MIN_NUMBER).append(',');
        json.append("\"max\":").append(Game.MAX_NUMBER);

        if (session.isFinished()) {
            json.append(",\"secretNumber\":").append(session.getSecretNumber());
        }
        json.append("}");

        sendJson(exchange, 200, json.toString());
    }

    private static String invalid(String message) {
        return "{\"status\":\"INVALID\",\"message\":\"" + escapeJson(message) + "\"}";
    }

    /**
     * Keeps memory bounded on a long running server.
     */
    private static void forgetOldSessions() {
        if (SESSIONS.size() < MAX_SESSIONS) {
            return;
        }
        // Finished rounds are worth nothing to a player, drop them first.
        SESSIONS.entrySet().removeIf(entry -> entry.getValue().isFinished());
        if (SESSIONS.size() >= MAX_SESSIONS) {
            SESSIONS.clear();
        }
    }

    // ------------------------------------------------------------------
    // Static files (the web folder)
    // ------------------------------------------------------------------

    private static void handleStaticFile(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }

        // Resolve inside the web folder only, so "../" cannot escape it.
        Path file = webRoot.resolve(path.substring(1)).normalize();
        if (!file.startsWith(webRoot) || !Files.isRegularFile(file)) {
            sendText(exchange, 404, "text/plain; charset=utf-8", "404 - File not found");
            return;
        }

        byte[] content = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", contentType(file.getFileName().toString()));
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, content.length);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(content);
        }
    }

    private static String contentType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (lower.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (lower.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".ico")) {
            return "image/x-icon";
        }
        if (lower.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        return "application/octet-stream";
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        sendText(exchange, status, "application/json; charset=utf-8", json);
    }

    private static void sendText(HttpExchange exchange, int status,
                                 String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** Reads a JSON string field such as {@code "gameId":"..."}. */
    private static String readStringField(String json, String field) {
        Matcher matcher = Pattern.compile(
                "\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Reads a JSON number field such as {@code "guess":42}. */
    private static Integer readIntField(String json, String field) {
        Matcher matcher = Pattern.compile(
                "\"" + Pattern.quote(field) + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String escapeJson(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
