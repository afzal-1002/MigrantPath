package com.foreignerwarsaw;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared base for full-context integration tests (brief §38). Runs against a real
 * Testcontainers-managed PostgreSQL 18 (via {@link TestcontainersConfiguration}) *and* a real,
 * throwaway Testcontainers-managed Mailpit instance - not the developer's own {@code docker
 * compose} Mailpit, so the suite is self-contained and never depends on a human having started
 * anything manually first (brief §39). Email content is fetched through Mailpit's own JSON API
 * ({@link #findLatestMessageTo}), never assumed - the same "prove it" standard as everything else
 * in this phase.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Testcontainers
public abstract class AbstractIntegrationTest {

  @Container
  static final GenericContainer<?> mailpit =
      new GenericContainer<>(DockerImageName.parse("axllent/mailpit:latest"))
          .withExposedPorts(1025, 8025);

  @DynamicPropertySource
  static void mailProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.mail.host", mailpit::getHost);
    registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
  }

  @Autowired protected MockMvc mockMvc;
  @Autowired protected ObjectMapper objectMapper;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @BeforeEach
  void resetMailpit() throws IOException, InterruptedException {
    httpClient.send(
        HttpRequest.newBuilder(URI.create(mailpitApiBase() + "/api/v1/messages")).DELETE().build(),
        HttpResponse.BodyHandlers.discarding());
  }

  protected String mailpitApiBase() {
    return "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025);
  }

  /**
   * Polls Mailpit's search API (short, bounded retries - email delivery to a local SMTP catcher is
   * fast but not synchronous with the HTTP response that triggered it) and returns the HTML body of
   * the newest message sent to {@code toEmail}, or {@code null} if none arrived in time.
   */
  protected String findLatestMessageTo(String toEmail) throws IOException, InterruptedException {
    String encoded = URLEncoder.encode("to:" + toEmail, StandardCharsets.UTF_8);
    URI searchUri = URI.create(mailpitApiBase() + "/api/v1/search?query=" + encoded);

    for (int attempt = 0; attempt < 20; attempt++) {
      HttpResponse<String> response =
          httpClient.send(
              HttpRequest.newBuilder(searchUri).GET().build(),
              HttpResponse.BodyHandlers.ofString());
      JsonNode root = objectMapper.readTree(response.body());
      JsonNode messages = root.path("messages");
      if (messages.isArray() && !messages.isEmpty()) {
        String id = messages.get(0).path("ID").asText();
        return fetchMessageHtml(id);
      }
      Thread.sleep(Duration.ofMillis(250));
    }
    return null;
  }

  private String fetchMessageHtml(String messageId) throws IOException, InterruptedException {
    URI messageUri = URI.create(mailpitApiBase() + "/api/v1/message/" + messageId);
    HttpResponse<String> response =
        httpClient.send(
            HttpRequest.newBuilder(messageUri).GET().build(), HttpResponse.BodyHandlers.ofString());
    return objectMapper.readTree(response.body()).path("HTML").asText();
  }
}
