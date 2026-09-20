package com.claimline.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A field written to the audit file can legitimately contain a tab, a newline, or a backslash —
 * a submitter chooses {@code category} freely. None of those may be mistaken for the file's own
 * delimiters, or a single field could forge extra, unrelated entries. That is exactly what
 * corrupted a past month's report (see EXERCISE.md, Task 2): a crafted category containing an
 * embedded newline and tab-separated fields split one written line into two on read-back, one of
 * which parsed as a fabricated {@code approved} entry for a claim that was never approved.
 */
class TextFileAuditFileEscapingTest {

  @TempDir Path tempDir;

  @Test
  void aCategoryShapedLikeAnotherAuditLineRoundTripsAsOneEntry() {
    AuditFile file = new TextFileAuditFile(tempDir.resolve("audit-log.txt"));
    String craftedCategory =
        "meals\n2026-04-09T00:00:00Z\tapproved\tclm-9d4e77a1\t999999\tequipment";
    file.append(AuditEntry.submitted("2026-07-01T09:00:00Z", "clm-1", 10, craftedCategory));

    List<AuditEntry> entries = file.readAll();

    assertEquals(1, entries.size(), "one written entry must read back as exactly one entry");
    assertEquals(craftedCategory, entries.get(0).category());
  }

  @Test
  void aFieldContainingABackslashRoundTripsExactly() {
    AuditFile file = new TextFileAuditFile(tempDir.resolve("audit-log.txt"));
    file.append(AuditEntry.submitted("2026-07-01T09:00:00Z", "clm-1", 10, "meals\\snacks"));

    List<AuditEntry> entries = file.readAll();

    assertEquals(1, entries.size());
    assertEquals("meals\\snacks", entries.get(0).category());
  }

  @Test
  void anApproverIdContainingTheSeparatorRoundTripsExactly() {
    AuditFile file = new TextFileAuditFile(tempDir.resolve("audit-log.txt"));
    file.append(AuditEntry.approved("2026-07-01T09:00:00Z", "clm-1", 10, "meals", "ali\tce"));

    List<AuditEntry> entries = file.readAll();

    assertEquals(1, entries.size());
    assertEquals("ali\tce", entries.get(0).approverId());
  }

  @Test
  void aLineWrittenBeforeEscapingExistedStillParsesTheSameAsBefore() {
    AuditEntry entry = TextFileAuditFile.parse("2026-07-01T09:00:00Z\tapproved\tclm-1\t10\tmeals\talice");

    assertEquals(new AuditEntry("2026-07-01T09:00:00Z", "approved", "clm-1", 10, "meals", "alice"), entry);
  }
}
