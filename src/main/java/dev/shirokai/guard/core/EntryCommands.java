package dev.shirokai.guard.core;

import java.util.*;

/** Exact commands, not prefixes. Preserve whitespace and case inside arguments/quotes. */
public final class EntryCommands {
  private EntryCommands() {}
  public static String normalize(String input) {
    if (input == null) throw new IllegalArgumentException("Comando ausente");
    String s = input.strip().replaceFirst("^/", "");
    if (s.isBlank() || s.length() > 256 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0 || s.contains("*"))
      throw new IllegalArgumentException("Comando exato de 1..256 caracteres, sem curingas ou quebras de linha");
    var out = new StringBuilder();
    boolean quote = false, escape = false, space = false;
    for (char ch : s.toCharArray()) {
      if (!quote && Character.isWhitespace(ch)) { space = out.length() > 0; continue; }
      if (space) { out.append(' '); space = false; }
      out.append(ch);
      if (escape) { escape = false; continue; }
      if (quote && ch == '\\') escape = true;
      else if (ch == '"') quote = !quote;
    }
    if (quote || escape) throw new IllegalArgumentException("Aspas inválidas");
    String result = out.toString();
    int end = result.indexOf(' ');
    if (end < 0) end = result.length();
    String root = result.substring(0,end);
    if (!root.matches("[A-Za-z0-9_:.\\-]+")) throw new IllegalArgumentException("Comando inválido");
    return root.toLowerCase(Locale.ROOT) + result.substring(end);
  }
  public static Set<String> list(Region region) {
    String value = region.flags().getOrDefault(Flag.ENTRY_COMMANDS,Map.of()).get(Region.Group.ALL);
    if (value == null) return Set.of();
    var entries = new HashSet<String>();
    for (String s : value.split("\n")) entries.add(normalize(s));
    return Set.copyOf(entries);
  }
}
