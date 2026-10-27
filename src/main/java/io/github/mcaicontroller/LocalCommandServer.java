package io.github.mcaicontroller;

import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

final class LocalCommandServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalCommandServer.class);
    private static final int DEFAULT_PORT = 32123;
    private static final String CONFIG_NAME = "mc_ai_controller.properties";

    private final Function<String, java.util.concurrent.CompletableFuture<String>> commandHandler;
    private volatile boolean running;
    private int port;
    private String token;

    LocalCommandServer(Function<String, java.util.concurrent.CompletableFuture<String>> commandHandler) {
        this.commandHandler = commandHandler;
    }

    void start() {
        try {
            loadOrCreateConfig();
        } catch (IOException error) {
            LOGGER.error("Could not initialize MC AI Controller config", error);
            return;
        }

        running = true;
        Thread thread = new Thread(this::serve, "mc-ai-controller-local-server");
        thread.setDaemon(true);
        thread.start();
    }

    private void serve() {
        try (ServerSocket server = new ServerSocket(port, 8, InetAddress.getLoopbackAddress())) {
            LOGGER.info("MC AI Controller listening on 127.0.0.1:{}", port);
            while (running) {
                try {
                    handle(server.accept());
                } catch (IOException error) {
                    if (running) LOGGER.warn("Local controller connection failed", error);
                }
            }
        } catch (IOException error) {
            LOGGER.error("MC AI Controller could not bind to 127.0.0.1:{}", port, error);
        }
    }

    private void handle(Socket socket) {
        try (socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            socket.setSoTimeout(7000);
            String request = reader.readLine();
            String response;
            if (request == null) {
                response = "错误：请求为空。";
            } else {
                int separator = request.indexOf('\t');
                if (separator < 1 || !constantTimeEquals(token, request.substring(0, separator))) {
                    response = "错误：本机连接令牌不正确。";
                } else {
                    String command = request.substring(separator + 1);
                    response = commandHandler.apply(command).get(6, TimeUnit.SECONDS);
                }
            }
            writer.write(response.replace('\n', ' '));
            writer.newLine();
            writer.flush();
        } catch (Exception error) {
            LOGGER.warn("Failed to process local controller command", error);
        }
    }

    private void loadOrCreateConfig() throws IOException {
        Path config = FMLPaths.CONFIGDIR.get().resolve(CONFIG_NAME);
        Properties properties = new Properties();
        if (Files.isRegularFile(config)) {
            try (var input = Files.newInputStream(config)) {
                properties.load(input);
            }
        }

        port = parsePort(properties.getProperty("port", Integer.toString(DEFAULT_PORT)));
        token = properties.getProperty("token", "").trim();
        if (token.length() < 24) token = generateToken();

        properties.setProperty("port", Integer.toString(port));
        properties.setProperty("token", token);
        properties.setProperty("bind", "127.0.0.1");

        Files.createDirectories(config.getParent());
        Path temporary = config.resolveSibling(config.getFileName() + ".tmp");
        try (var output = Files.newOutputStream(temporary)) {
            properties.store(output, "MC AI Controller local bridge. Keep token private.");
        }
        try {
            Files.move(temporary, config, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, config, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static int parsePort(String raw) {
        try {
            int value = Integer.parseInt(raw.trim());
            return value >= 1024 && value <= 65535 ? value : DEFAULT_PORT;
        } catch (NumberFormatException ignored) {
            return DEFAULT_PORT;
        }
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = actual.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(left, right);
    }
}
