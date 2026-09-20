package com.claimline.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.claimline.audit.AuditFile;
import com.claimline.audit.TextFileAuditFile;
import com.claimline.store.Approval;
import com.claimline.store.ApprovalState;
import com.claimline.store.ClaimStore;
import com.claimline.store.InMemoryClaimStore;
import com.claimline.support.TestServices;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Historical claims were already approved before per-approval tracking existed, so seeding has
 * to reconstruct one approval for each from the audit trail that already recorded it.
 */
class SeedDataApprovalsTest {

  @TempDir Path tempDir;

  @Test
  void backfillsOneApprovalForAnAlreadyApprovedSeedClaimFromTheAuditFile() throws IOException {
    Path auditFilePath = tempDir.resolve("audit-log.txt");
    Files.writeString(
        auditFilePath,
        "2026-04-03T09:14:22Z\tsubmitted\tclm-4a1c9e02\t42\ttravel\t\n"
            + "2026-04-03T11:02:47Z\tapproved\tclm-4a1c9e02\t42\ttravel\talice\n",
        StandardCharsets.UTF_8);
    AuditFile auditFile = new TextFileAuditFile(auditFilePath);
    ClaimStore store = new InMemoryClaimStore();

    SeedData.load().seedInto(store, TestServices.thresholds(), auditFile);

    ApprovalState state = store.approvalsFor("clm-4a1c9e02");
    assertEquals(1, state.approvals().size());
    assertEquals(new Approval("alice", "2026-04-03T11:02:47Z"), state.approvals().get(0));
    assertEquals(1, state.approvalsRequired());
  }

  @Test
  void fallsBackToUnknownWhenNoMatchingAuditEntryExists() throws IOException {
    Path auditFilePath = tempDir.resolve("audit-log.txt");
    Files.writeString(auditFilePath, "", StandardCharsets.UTF_8);
    AuditFile auditFile = new TextFileAuditFile(auditFilePath);
    ClaimStore store = new InMemoryClaimStore();

    SeedData.load().seedInto(store, TestServices.thresholds(), auditFile);

    ApprovalState state = store.approvalsFor("clm-4a1c9e02");
    assertEquals(1, state.approvals().size());
    assertEquals("unknown", state.approvals().get(0).timestamp());
  }
}
