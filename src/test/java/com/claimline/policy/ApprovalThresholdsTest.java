package com.claimline.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.claimline.policy.ApprovalThresholds.Threshold;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApprovalThresholdsTest {

  @TempDir Path tempDir;

  private final ApprovalThresholds thresholds =
      new ApprovalThresholds(
          List.of(new Threshold(0, 1), new Threshold(1_000, 2), new Threshold(10_000, 3)));

  @Test
  void claimsUnderOneThousandNeedOneApproval() {
    assertEquals(1, thresholds.requiredApprovals(1));
    assertEquals(1, thresholds.requiredApprovals(999));
  }

  @Test
  void claimsFromOneThousandToJustUnderTenThousandNeedTwoApprovals() {
    assertEquals(2, thresholds.requiredApprovals(1_000));
    assertEquals(2, thresholds.requiredApprovals(9_999));
  }

  @Test
  void claimsOfTenThousandAndAboveNeedThreeApprovals() {
    assertEquals(3, thresholds.requiredApprovals(10_000));
    assertEquals(3, thresholds.requiredApprovals(20_000));
  }

  @Test
  void rejectsThresholdsThatDoNotCoverZero() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ApprovalThresholds(List.of(new Threshold(100, 1))));
  }

  @Test
  void loadsThresholdsFromAJsonFileOwnedByFinance() throws IOException {
    Path file = tempDir.resolve("approval-thresholds.json");
    Files.writeString(
        file,
        """
        {
          "thresholds": [
            { "minAmount": 0, "approvalsRequired": 1 },
            { "minAmount": 1000, "approvalsRequired": 2 },
            { "minAmount": 10000, "approvalsRequired": 3 }
          ]
        }
        """,
        StandardCharsets.UTF_8);

    ApprovalThresholds loaded = ApprovalThresholds.load(file);

    assertEquals(1, loaded.requiredApprovals(999));
    assertEquals(2, loaded.requiredApprovals(1_000));
    assertEquals(3, loaded.requiredApprovals(10_000));
  }

  @Test
  void reloadingAfterFinanceEditsTheFileChangesThePolicy() throws IOException {
    Path file = tempDir.resolve("approval-thresholds.json");
    Files.writeString(
        file,
        """
        { "thresholds": [ { "minAmount": 0, "approvalsRequired": 1 } ] }
        """,
        StandardCharsets.UTF_8);
    assertEquals(1, ApprovalThresholds.load(file).requiredApprovals(15_000));

    Files.writeString(
        file,
        """
        { "thresholds": [ { "minAmount": 0, "approvalsRequired": 1 }, { "minAmount": 5000, "approvalsRequired": 3 } ] }
        """,
        StandardCharsets.UTF_8);

    assertEquals(3, ApprovalThresholds.load(file).requiredApprovals(15_000));
  }
}
