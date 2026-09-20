package com.claimline.service;

import com.claimline.audit.AuditEntry;
import com.claimline.audit.AuditFile;
import com.claimline.policy.ApprovalPolicy;
import com.claimline.policy.ApprovalThresholds;
import com.claimline.store.Approval;
import com.claimline.store.ApprovalState;
import com.claimline.store.Claim;
import com.claimline.store.ClaimStore;
import java.util.UUID;

/** Submits, approves, and reads expense claims. */
public final class ClaimService {

  private final ClaimStore claims;
  private final ApprovalPolicy approvalPolicy;
  private final ApprovalThresholds approvalThresholds;
  private final AuditFile auditFile;
  private final Clock clock;

  public ClaimService(
      ClaimStore claims,
      ApprovalPolicy approvalPolicy,
      ApprovalThresholds approvalThresholds,
      AuditFile auditFile,
      Clock clock) {
    this.claims = claims;
    this.approvalPolicy = approvalPolicy;
    this.approvalThresholds = approvalThresholds;
    this.auditFile = auditFile;
    this.clock = clock;
  }

  public Claim submit(SubmitClaimRequest request) {
    if (request.submitterId() == null || request.submitterId().isBlank()) {
      throw new IllegalArgumentException("submitterId is required");
    }
    if (request.category() == null || request.category().isBlank()) {
      throw new IllegalArgumentException("category is required");
    }
    if (request.amount() <= 0) {
      throw new IllegalArgumentException("amount must be positive");
    }
    if (request.amount() > 20_000) {
      throw new IllegalArgumentException("amount exceeds the maximum claimable amount");
    }

    Claim claim =
        new Claim(
            "clm-" + UUID.randomUUID().toString().substring(0, 8),
            request.submitterId(),
            request.amount(),
            request.category(),
            Claim.PENDING,
            null);
    claims.save(claim);
    claims.initApprovals(claim.id(), approvalThresholds.requiredApprovals(claim.amount()));
    auditFile.append(
        AuditEntry.submitted(clock.nowIso(), claim.id(), claim.amount(), claim.category()));
    return claim;
  }

  /**
   * Records one approval towards a claim. A claim needs as many approvals as {@link
   * ApprovalThresholds} says for its amount; it stays {@code pending} until it has all of them,
   * and only then is a single {@code approved} audit entry written, so the monthly report still
   * counts it once.
   */
  public synchronized Claim approve(String claimId, String approverId) {
    Claim claim =
        claims.findById(claimId).orElseThrow(() -> new ClaimNotFoundException("unknown claim"));

    if (Claim.APPROVED.equals(claim.status())) {
      throw new ApprovalDeniedException("claim is already fully approved");
    }
    if (!approvalPolicy.check(approverId, claim.amount())) {
      throw new ApprovalDeniedException("approver is not authorised for this amount");
    }

    ApprovalState state = claims.approvalsFor(claimId);
    if (state.approvals().stream().anyMatch(a -> a.approverId().equals(approverId))) {
      throw new ApprovalDeniedException("approver has already approved this claim");
    }

    String now = clock.nowIso();
    claims.addApproval(claimId, new Approval(approverId, now));
    auditFile.append(
        AuditEntry.approvalGiven(now, claimId, claim.amount(), claim.category(), approverId));

    boolean fullyApproved = state.approvals().size() + 1 >= state.approvalsRequired();
    if (!fullyApproved) {
      return claim;
    }

    Claim approved = claim.approvedBy(approverId);
    claims.save(approved);
    auditFile.append(
        AuditEntry.approved(now, approved.id(), approved.amount(), approved.category(), approverId));
    return approved;
  }

  public Claim get(String claimId) {
    return claims.findById(claimId).orElseThrow(() -> new ClaimNotFoundException("unknown claim"));
  }

  /** Every approval a claim has received, and how many it needs in total. */
  public ApprovalState approvals(String claimId) {
    get(claimId);
    return claims.approvalsFor(claimId);
  }
}
