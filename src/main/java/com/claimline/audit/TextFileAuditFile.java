package com.claimline.audit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * An {@link AuditFile} stored as one entry per line, with fields separated by tabs.
 *
 * <p>A field may legitimately contain a tab or a newline (a submitter can type anything into
 * {@code category}), so each field is escaped on write and unescaped on read. Without this, a
 * crafted field could be mistaken for the file's own delimiters and forge extra, unrelated
 * entries — which is exactly what corrupted a past month's report.
 */
public final class TextFileAuditFile implements AuditFile {

  private static final String SEPARATOR = "\t";
  private static final int FIELD_COUNT = 6;

  private final Path file;

  public TextFileAuditFile(Path file) {
    this.file = file;
  }

  @Override
  public void append(AuditEntry entry) {
    // Fields are written in a fixed order.
    String line =
        escape(entry.timestamp())
            + SEPARATOR
            + escape(entry.action())
            + SEPARATOR
            + escape(entry.claimId())
            + SEPARATOR
            + entry.amount()
            + SEPARATOR
            + escape(entry.category())
            + SEPARATOR
            + escape(entry.approverId())
            + System.lineSeparator();
    try {
      Files.createDirectories(file.toAbsolutePath().getParent());
      Files.writeString(
          file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new UncheckedIOException("could not append to the audit file", e);
    }
  }

  @Override
  public List<AuditEntry> readAll() {
    if (!Files.exists(file)) {
      return List.of();
    }
    List<AuditEntry> entries = new ArrayList<>();
    try {
      for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
        if (line.isBlank()) {
          continue;
        }
        AuditEntry entry = parse(line);
        if (entry != null) {
          entries.add(entry);
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("could not read the audit file", e);
    }
    return entries;
  }

  /** Reads a single line, returning null if it does not match the expected fields. */
  static AuditEntry parse(String line) {
    String[] fields = line.split(SEPARATOR, -1);
    if (fields.length != FIELD_COUNT) {
      return null;
    }
    long amount;
    try {
      amount = Long.parseLong(fields[3]);
    } catch (NumberFormatException e) {
      return null;
    }
    return new AuditEntry(
        unescape(fields[0]),
        unescape(fields[1]),
        unescape(fields[2]),
        amount,
        unescape(fields[4]),
        unescape(fields[5]));
  }

  /**
   * Replaces the characters that would otherwise be mistaken for this file's own delimiters
   * (a tab, a newline, a carriage return) with a backslash escape, and escapes a literal
   * backslash itself so the scheme stays reversible.
   */
  private static String escape(String field) {
    return field
        .replace("\\", "\\\\")
        .replace("\t", "\\t")
        .replace("\n", "\\n")
        .replace("\r", "\\r");
  }

  /**
   * Reverses {@link #escape}. A field written before escaping existed contains none of these
   * escape sequences, so it comes back unchanged.
   */
  private static String unescape(String field) {
    StringBuilder unescaped = new StringBuilder(field.length());
    for (int i = 0; i < field.length(); i++) {
      char c = field.charAt(i);
      if (c == '\\' && i + 1 < field.length()) {
        char next = field.charAt(i + 1);
        switch (next) {
          case 't' -> unescaped.append('\t');
          case 'n' -> unescaped.append('\n');
          case 'r' -> unescaped.append('\r');
          case '\\' -> unescaped.append('\\');
          default -> {
            unescaped.append(c);
            continue;
          }
        }
        i++;
      } else {
        unescaped.append(c);
      }
    }
    return unescaped.toString();
  }
}
