package com.claimline.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.claimline.support.TestServices;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the multi-step approval fields on {@code GET /claims/{id}}: every approval a claim has
 * received, who gave it and when, and how many are still needed.
 */
class ClaimApiApprovalsTest {

  @TempDir Path tempDir;

  private final Gson gson = new Gson();
  private final HttpClient client = HttpClient.newHttpClient();
  private HttpServer server;
  private String baseUrl;

  @BeforeEach
  void startServer() throws IOException {
    Path auditFile = tempDir.resolve("audit-log.txt");
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    new ClaimApi(TestServices.claimService(auditFile), TestServices.reportService(auditFile))
        .register(server);
    server.start();
    baseUrl = "http://localhost:" + server.getAddress().getPort();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void aClaimNeedingTwoApprovalsStaysPendingAfterOneAndListsItsApproval() throws Exception {
    String id = json(post("/claims", claimBody(1_500, "equipment"))).get("id").getAsString();

    JsonObject afterFirst = json(post("/claims/" + id + "/approve", approveBody("bharat")));

    assertEquals("pending", afterFirst.get("status").getAsString());
    // Gson omits null fields by default, so a still-null approvedBy is simply absent.
    assertFalse(afterFirst.has("approvedBy"));
    assertEquals(2, afterFirst.get("approvalsRequired").getAsInt());
    JsonArray approvals = afterFirst.getAsJsonArray("approvals");
    assertEquals(1, approvals.size());
    assertEquals("bharat", approvals.get(0).getAsJsonObject().get("approverId").getAsString());
    assertFalse(approvals.get(0).getAsJsonObject().get("timestamp").getAsString().isBlank());
  }

  @Test
  void readingBackShowsEveryApprovalOnceFullyApproved() throws Exception {
    String id = json(post("/claims", claimBody(1_500, "equipment"))).get("id").getAsString();
    post("/claims/" + id + "/approve", approveBody("bharat"));
    post("/claims/" + id + "/approve", approveBody("dana"));

    JsonObject fetched = json(get("/claims/" + id));

    assertEquals("approved", fetched.get("status").getAsString());
    assertEquals("dana", fetched.get("approvedBy").getAsString());
    JsonArray approvals = fetched.getAsJsonArray("approvals");
    assertEquals(2, approvals.size());
    assertEquals("bharat", approvals.get(0).getAsJsonObject().get("approverId").getAsString());
    assertEquals("dana", approvals.get(1).getAsJsonObject().get("approverId").getAsString());
  }

  @Test
  void aFreshlySubmittedClaimHasNoApprovalsYet() throws Exception {
    JsonObject submitted = json(post("/claims", claimBody(11_000, "equipment")));

    assertEquals(0, submitted.getAsJsonArray("approvals").size());
    assertEquals(3, submitted.get("approvalsRequired").getAsInt());
  }

  @Test
  void rejectsASecondApprovalFromTheSameApprover() throws Exception {
    String id = json(post("/claims", claimBody(1_500, "equipment"))).get("id").getAsString();
    post("/claims/" + id + "/approve", approveBody("bharat"));

    assertEquals(403, post("/claims/" + id + "/approve", approveBody("bharat")).statusCode());
  }

  private static String claimBody(long amount, String category) {
    return "{\"submitterId\":\"erin\",\"amount\":"
        + amount
        + ",\"category\":\""
        + category
        + "\",\"description\":\"a claim\"}";
  }

  private static String approveBody(String approverId) {
    return "{\"approverId\":\"" + approverId + "\"}";
  }

  private JsonObject json(HttpResponse<String> response) {
    return gson.fromJson(response.body(), JsonObject.class);
  }

  private HttpResponse<String> post(String path, String body) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> get(String path) throws Exception {
    HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
