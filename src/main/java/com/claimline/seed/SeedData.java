package com.claimline.seed;

import com.claimline.audit.AuditEntry;
import com.claimline.audit.AuditFile;
import com.claimline.policy.ApprovalPolicy;
import com.claimline.policy.ApprovalThresholds;
import com.claimline.store.Approval;
import com.claimline.store.Claim;
import com.claimline.store.ClaimStore;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loads the approvers and the claims the service already holds. */
public final class SeedData {

  private static final String APPROVERS = "/com/claimline/seed/approvers.json";
  private static final String CLAIMS = "/com/claimline/seed/claims.json";

  private final List<SeedApprover> approvers;
  private final List<SeedClaim> claims;

  private SeedData(List<SeedApprover> approvers, List<SeedClaim> claims) {
    this.approvers = approvers;
    this.claims = claims;
  }

  public static SeedData load() {
    return new SeedData(
        read(APPROVERS, ApproverFixture.class).approvers, read(CLAIMS, ClaimFixture.class).claims);
  }

  public ApprovalPolicy toApprovalPolicy() {
    Map<String, Long> limits = new HashMap<>();
    for (SeedApprover approver : approvers) {
      limits.put(approver.approverId, approver.limit);
    }
    return new ApprovalPolicy(limits);
  }

  /**
   * Seeds claims into the store, along with how many approvals each needs and, for claims that
   * are already approved, the one approval on record for it. That approval's timestamp is looked
   * up from the audit file, which already holds it from when the claim was really approved;
   * {@code "unknown"} is used only if no matching entry can be found there.
   */
  public void seedInto(ClaimStore store, ApprovalThresholds thresholds, AuditFile auditFile) {
    Map<String, String> approvedTimestampByClaimId = approvedTimestamps(auditFile);
    for (SeedClaim claim : claims) {
      store.save(
          new Claim(
              claim.id,
              claim.submitterId,
              claim.amount,
              claim.category,
              claim.status,
              claim.approvedBy));
      store.initApprovals(claim.id, thresholds.requiredApprovals(claim.amount));
      if (Claim.APPROVED.equals(claim.status) && claim.approvedBy != null
          && !claim.approvedBy.isBlank()) {
        String timestamp = approvedTimestampByClaimId.getOrDefault(claim.id, "unknown");
        store.addApproval(claim.id, new Approval(claim.approvedBy, timestamp));
      }
    }
  }

  private static Map<String, String> approvedTimestamps(AuditFile auditFile) {
    Map<String, String> timestampByClaimId = new HashMap<>();
    for (AuditEntry entry : auditFile.readAll()) {
      if (AuditEntry.APPROVED.equals(entry.action())) {
        timestampByClaimId.put(entry.claimId(), entry.timestamp());
      }
    }
    return timestampByClaimId;
  }

  private static <T> T read(String resource, Class<T> type) {
    try (InputStream in = SeedData.class.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("fixture not found: " + resource);
      }
      return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
    } catch (IOException e) {
      throw new IllegalStateException("failed to load fixture: " + resource, e);
    }
  }

  private static final class ApproverFixture {
    List<SeedApprover> approvers;
  }

  private static final class ClaimFixture {
    List<SeedClaim> claims;
  }

  private static final class SeedApprover {
    String approverId;
    long limit;
  }

  private static final class SeedClaim {
    String id;
    String submitterId;
    long amount;
    String category;
    String status;
    String approvedBy;
  }
}
