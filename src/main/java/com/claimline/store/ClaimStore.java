package com.claimline.store;

import java.util.Optional;

/** Stores expense claims and the approvals each one has received. */
public interface ClaimStore {

  void save(Claim claim);

  Optional<Claim> findById(String id);

  /** Fixes how many approvals a claim needs, at submission time. */
  void initApprovals(String claimId, int approvalsRequired);

  /** Records one more approval given to a claim. */
  void addApproval(String claimId, Approval approval);

  /** Every approval a claim has received so far, and how many it needs in total. */
  ApprovalState approvalsFor(String claimId);
}
