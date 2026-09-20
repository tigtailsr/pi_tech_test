package com.claimline.policy;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decides how many approvals a claim needs, based on its amount. Finance owns the thresholds and
 * maintains them as a JSON file on disk, so they can change without a code change.
 */
public final class ApprovalThresholds {

  /** Claims of at least {@code minAmount} need {@code approvalsRequired} approvals. */
  public record Threshold(long minAmount, int approvalsRequired) {}

  private final List<Threshold> ascending;

  public ApprovalThresholds(List<Threshold> thresholds) {
    if (thresholds == null || thresholds.isEmpty()) {
      throw new IllegalArgumentException("at least one threshold is required");
    }
    List<Threshold> sorted = new ArrayList<>(thresholds);
    sorted.sort(Comparator.comparingLong(Threshold::minAmount));
    if (sorted.getFirst().minAmount() > 0) {
      throw new IllegalArgumentException("thresholds must cover every amount from 0 upward");
    }
    for (Threshold threshold : sorted) {
      if (threshold.approvalsRequired() < 1) {
        throw new IllegalArgumentException("approvalsRequired must be at least 1");
      }
    }
    this.ascending = List.copyOf(sorted);
  }

  /** How many approvals a claim of this amount needs. */
  public int requiredApprovals(long amount) {
    int required = ascending.getFirst().approvalsRequired();
    for (Threshold threshold : ascending) {
      if (amount < threshold.minAmount()) {
        break;
      }
      required = threshold.approvalsRequired();
    }
    return required;
  }

  /** Loads the thresholds finance maintains as a JSON file, formatted as in {@code load}'s javadoc. */
  public static ApprovalThresholds load(Path file) {
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      Fixture fixture = new Gson().fromJson(reader, Fixture.class);
      if (fixture == null || fixture.thresholds == null || fixture.thresholds.isEmpty()) {
        throw new IllegalStateException("no thresholds found in " + file);
      }
      List<Threshold> thresholds = new ArrayList<>();
      for (ThresholdFixture t : fixture.thresholds) {
        thresholds.add(new Threshold(t.minAmount, t.approvalsRequired));
      }
      return new ApprovalThresholds(thresholds);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read approval thresholds from " + file, e);
    }
  }

  private static final class Fixture {
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    List<ThresholdFixture> thresholds;
  }

  private static final class ThresholdFixture {
    long minAmount;
    int approvalsRequired;
  }
}
