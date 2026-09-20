package com.claimline.store;

import java.util.List;

/** Every approval a claim has received so far, and how many it needs in total. */
public record ApprovalState(List<Approval> approvals, int approvalsRequired) {

  public boolean isComplete() {
    return approvals.size() >= approvalsRequired;
  }
}
