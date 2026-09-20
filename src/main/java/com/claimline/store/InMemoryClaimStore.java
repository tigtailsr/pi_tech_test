package com.claimline.store;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory {@link ClaimStore} backed by concurrent maps. */
public final class InMemoryClaimStore implements ClaimStore {

  private final ConcurrentMap<String, Claim> claims = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, List<Approval>> approvalsByClaim = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Integer> approvalsRequiredByClaim = new ConcurrentHashMap<>();

  @Override
  public void save(Claim claim) {
    claims.put(claim.id(), claim);
  }

  @Override
  public Optional<Claim> findById(String id) {
    return Optional.ofNullable(claims.get(id));
  }

  @Override
  public void initApprovals(String claimId, int approvalsRequired) {
    approvalsRequiredByClaim.put(claimId, approvalsRequired);
    approvalsByClaim.putIfAbsent(claimId, new CopyOnWriteArrayList<>());
  }

  @Override
  public void addApproval(String claimId, Approval approval) {
    approvalsByClaim.computeIfAbsent(claimId, id -> new CopyOnWriteArrayList<>()).add(approval);
  }

  @Override
  public ApprovalState approvalsFor(String claimId) {
    List<Approval> approvals = approvalsByClaim.getOrDefault(claimId, List.of());
    int approvalsRequired = approvalsRequiredByClaim.getOrDefault(claimId, 1);
    return new ApprovalState(List.copyOf(approvals), approvalsRequired);
  }
}
