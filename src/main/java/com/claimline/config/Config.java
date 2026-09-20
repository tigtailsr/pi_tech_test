package com.claimline.config;

import java.nio.file.Path;

/** Runtime configuration for the service. */
public record Config(int port, Path auditFile, Path approvalThresholdsFile) {

  private static final Path AUDIT_FILE = Path.of("data/audit-log.txt");

  // Owned by finance; how many approvals a claim needs by amount. Editing this file changes the
  // policy on the next restart, without touching any code.
  private static final Path APPROVAL_THRESHOLDS_FILE = Path.of("config/approval-thresholds.json");

  public static Config fromEnvironment() {
    String port = System.getenv("CLAIMLINE_PORT");
    return new Config(
        port == null || port.isBlank() ? 8080 : Integer.parseInt(port),
        AUDIT_FILE,
        APPROVAL_THRESHOLDS_FILE);
  }
}
