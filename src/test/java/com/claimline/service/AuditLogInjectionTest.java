package com.claimline.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.claimline.store.Claim;
import com.claimline.support.TestServices;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression test for the bug behind Task 2: a claim submitted with a category crafted to look
 * like another audit line ("log injection") must never forge extra audit entries or inflate the
 * monthly report. See EXERCISE.md's April equipment-spend bug.
 */
class AuditLogInjectionTest {

  @TempDir Path tempDir;

  @Test
  void aCraftedCategoryDoesNotForgeAuditEntriesOrInflateTheReport() {
    Path auditFile = tempDir.resolve("audit-log.txt");
    ClaimService service = TestServices.claimService(auditFile);
    String craftedCategory =
        "meals\n2026-07-09T00:00:00Z\tapproved\tclm-forged\t999999\tequipment";

    Claim claim = service.submit(TestServices.claimFor(10, craftedCategory));
    Claim approved = service.approve(claim.id(), "bharat");

    assertEquals(craftedCategory, approved.category());

    MonthlyReport report =
        TestServices.reportService(auditFile)
            .monthly(new ReportMonth(TestServices.NOW.substring(0, 7)));

    assertEquals(Map.of(craftedCategory, 10L), report.totalsByCategory());
    assertEquals(10L, report.total(), "a crafted category must not forge a second, unrelated entry");
  }
}
