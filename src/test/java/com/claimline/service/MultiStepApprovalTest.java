package com.claimline.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.claimline.store.Claim;
import com.claimline.support.TestServices;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Approvals go in steps by the size of the claim: {@link TestServices#thresholds()} mirrors the
 * table finance ships in {@code config/approval-thresholds.json}.
 */
class MultiStepApprovalTest {

  @TempDir Path tempDir;

  private ClaimService service;
  private Path auditFile;

  @BeforeEach
  void setUp() {
    auditFile = tempDir.resolve("audit-log.txt");
    service = TestServices.claimService(auditFile);
  }

  @Test
  void aClaimUnderOneThousandStillNeedsOnlyOneApproval() {
    Claim claim = service.submit(TestServices.claimFor(999, "travel"));

    Claim approved = service.approve(claim.id(), "bharat");

    assertEquals(Claim.APPROVED, approved.status());
    assertEquals("bharat", approved.approvedBy());
    assertEquals(1, service.approvals(claim.id()).approvals().size());
  }

  @Test
  void aClaimBetweenOneThousandAndNineThousandNineNineNineStaysPendingAfterOneApproval() {
    Claim claim = service.submit(TestServices.claimFor(1_500, "equipment"));

    Claim afterFirstApproval = service.approve(claim.id(), "bharat");

    assertEquals(Claim.PENDING, afterFirstApproval.status());
    assertNull(afterFirstApproval.approvedBy());
    assertEquals(1, service.approvals(claim.id()).approvals().size());
    assertEquals(2, service.approvals(claim.id()).approvalsRequired());
  }

  @Test
  void aClaimBetweenOneThousandAndNineThousandNineNineNineIsApprovedAfterTwoDistinctApprovers() {
    Claim claim = service.submit(TestServices.claimFor(1_500, "equipment"));
    service.approve(claim.id(), "bharat");

    Claim afterSecondApproval = service.approve(claim.id(), "dana");

    assertEquals(Claim.APPROVED, afterSecondApproval.status());
    assertEquals("dana", afterSecondApproval.approvedBy());
    assertEquals(2, service.approvals(claim.id()).approvals().size());
  }

  @Test
  void aClaimOfTenThousandAndAboveNeedsThreeDistinctApprovers() {
    Claim claim = service.submit(TestServices.claimFor(15_000, "equipment"));

    service.approve(claim.id(), "eshe");
    Claim afterTwo = service.approve(claim.id(), "farouk");
    assertEquals(Claim.PENDING, afterTwo.status());

    Claim afterThree = service.approve(claim.id(), "gita");
    assertEquals(Claim.APPROVED, afterThree.status());
    assertEquals(3, service.approvals(claim.id()).approvals().size());
  }

  @Test
  void everyApprovalRecordsWhoGaveItAndWhen() {
    Claim claim = service.submit(TestServices.claimFor(1_500, "equipment"));
    service.approve(claim.id(), "bharat");
    service.approve(claim.id(), "dana");

    var approvals = service.approvals(claim.id()).approvals();

    assertEquals(2, approvals.size());
    assertEquals("bharat", approvals.get(0).approverId());
    assertEquals(TestServices.NOW, approvals.get(0).timestamp());
    assertEquals("dana", approvals.get(1).approverId());
    assertEquals(TestServices.NOW, approvals.get(1).timestamp());
  }

  @Test
  void rejectsTheSameApproverApprovingTwice() {
    Claim claim = service.submit(TestServices.claimFor(1_500, "equipment"));
    service.approve(claim.id(), "bharat");

    assertThrows(ApprovalDeniedException.class, () -> service.approve(claim.id(), "bharat"));
  }

  @Test
  void rejectsApprovingAClaimThatIsAlreadyFullyApproved() {
    Claim claim = service.submit(TestServices.claimFor(315, "travel"));
    service.approve(claim.id(), "bharat");

    assertThrows(ApprovalDeniedException.class, () -> service.approve(claim.id(), "dana"));
  }

  @Test
  void rejectsAnApproverOverTheirLimitOnAnyStep() {
    Claim claim = service.submit(TestServices.claimFor(1_500, "equipment"));

    assertThrows(ApprovalDeniedException.class, () -> service.approve(claim.id(), "alice"));
  }

  @Test
  void onlyTheFinalApprovalIsCountedInTheMonthlyReport() {
    Claim claim = service.submit(TestServices.claimFor(4_000, "equipment"));
    service.approve(claim.id(), "bharat");
    service.approve(claim.id(), "dana");

    MonthlyReport report =
        TestServices.reportService(auditFile).monthly(new ReportMonth(TestServices.NOW.substring(0, 7)));

    assertEquals(Map.of("equipment", 4_000L), report.totalsByCategory());
      assertEquals(4_000L, report.total(), "each approved claim must count once, for its own amount");
  }
}
