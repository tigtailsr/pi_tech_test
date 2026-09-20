package com.claimline.audit;

/** A single recorded change to a claim. */
public record AuditEntry(
    String timestamp,
    String action,
    String claimId,
    long amount,
    String category,
    String approverId) {

  public static final String SUBMITTED = "submitted";
  public static final String APPROVED = "approved";

  /** One approval towards a claim that needs more than one. Not counted in the monthly report. */
  public static final String APPROVAL_GIVEN = "approval";

  public static AuditEntry submitted(
      String timestamp, String claimId, long amount, String category) {
    return new AuditEntry(timestamp, SUBMITTED, claimId, amount, category, "");
  }

  public static AuditEntry approved(
      String timestamp, String claimId, long amount, String category, String approverId) {
    return new AuditEntry(timestamp, APPROVED, claimId, amount, category, approverId);
  }

  public static AuditEntry approvalGiven(
      String timestamp, String claimId, long amount, String category, String approverId) {
    return new AuditEntry(timestamp, APPROVAL_GIVEN, claimId, amount, category, approverId);
  }
}
