package com.claimline.http;

import java.util.List;
import java.util.Map;

/** Request and response bodies exchanged over HTTP. */
final class Dtos {

  private Dtos() {}

  record SubmitRequest(String submitterId, long amount, String category, String description) {}

  record ApproveRequest(String approverId) {}

  /** Who gave one approval, and when. */
  record ApprovalResponse(String approverId, String timestamp) {}

  record ClaimResponse(
      String id,
      String submitterId,
      long amount,
      String category,
      String status,
      String approvedBy,
      List<ApprovalResponse> approvals,
      int approvalsRequired) {}

  record ReportResponse(String month, Map<String, Long> totalsByCategory, long total) {}
}
