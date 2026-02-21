package ge.orderapp.integration.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public interface TelegramBotClient {
    boolean sendMessage(String htmlMessage);
}

@Component
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
class RealTelegramBotClient implements TelegramBotClient {

    private static final Logger log = LoggerFactory.getLogger(RealTelegramBotClient.class);

    @Value("${telegram.bot-token}")
    private String botToken;

    @Value("${telegram.chat-id}")
    private String chatId;

    @Value("${telegram.api-base-url:https://api.telegram.org}")
    private String apiBaseUrl;

    @Value("${telegram.connect-timeout-seconds:10}")
    private int connectTimeoutSeconds;

    @Value("${telegram.request-timeout-seconds:15}")
    private int requestTimeoutSeconds;

    private HttpClient httpClient;

    @PostConstruct
    public void initClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
    }

    @Override
    public boolean sendMessage(String htmlMessage) {
        try {
            String url = apiBaseUrl + "/bot" + botToken + "/sendMessage";
            String body = "chat_id=" + URLEncoder.encode(chatId, StandardCharsets.UTF_8)
                    + "&text=" + URLEncoder.encode(htmlMessage, StandardCharsets.UTF_8)
                    + "&parse_mode=HTML";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("Telegram message sent successfully");
                return true;
            } else {
                log.error("Telegram API error: {} {}", response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to send Telegram message: {}", e.getMessage());
            return false;
        }
    }
}

@Component
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "false", matchIfMissing = true)
class MockTelegramBotClient implements TelegramBotClient {

    private static final Logger log = LoggerFactory.getLogger(MockTelegramBotClient.class);

    @Override
    public boolean sendMessage(String htmlMessage) {
        log.info("=== MOCK TELEGRAM MESSAGE ===\n{}\n=== END TELEGRAM MESSAGE ===", htmlMessage);
        return false;
    }
}
