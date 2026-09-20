package com.claimline.store;

/** A single approval a claim has received: who gave it, and when. */
public record Approval(String approverId, String timestamp) {}
