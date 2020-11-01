package quarkus.datadog.trace.bootstrap.config.provider;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ConfigConverter {

  private static final Pattern COMMA_SEPARATED =
      Pattern.compile("(([^,:]+:[^,:]*,)*([^,:]+:[^,:]*),?)?");
  private static final Pattern SPACE_SEPARATED = Pattern.compile("((\\S+:\\S*)\\s+)*(\\S+:\\S*)?");
  private static final Pattern ILLEGAL_SPACE_SEPARATED = Pattern.compile("(:\\S+:)+");

  @NonNull
  static List<String> parseList(final String str) {
    if (str == null || str.trim().isEmpty()) {
      return Collections.emptyList();
    }

    final String[] tokens = str.split(",", -1);
    // Remove whitespace from each item.
    for (int i = 0; i < tokens.length; i++) {
      tokens[i] = tokens[i].trim();
    }
    return Collections.unmodifiableList(Arrays.asList(tokens));
  }

  @NonNull
  static Map<String, String> parseMap(final String str, final String settingName) {
    // If we ever want to have default values besides an empty map, this will need to change.
    if (str == null) {
      return Collections.emptyMap();
    }
    String trimmed = str.trim();
    if (trimmed.isEmpty()) {
      return Collections.emptyMap();
    }
    if (COMMA_SEPARATED.matcher(trimmed).matches()) {
      return parseMap(str, settingName, ",");
    }
    if (SPACE_SEPARATED.matcher(trimmed).matches()
        && !ILLEGAL_SPACE_SEPARATED.matcher(trimmed).find()) {
      return parseMap(str, settingName, "\\s+");
    }
    log.warn(
        "Invalid config for {}: '{}'. Must match 'key1:value1,key2:value2' or 'key1:value1 key2:value2'.",
        settingName,
        str);
    return Collections.emptyMap();
  }

  private static Map<String, String> parseMap(
      final String str, final String settingName, final String separator) {
    final String[] tokens = str.split(separator);
    final Map<String, String> map = newHashMap(tokens.length);

    for (final String token : tokens) {
      final String[] keyValue = token.split(":", 2);
      if (keyValue.length == 2) {
        final String key = keyValue[0].trim();
        final String value = keyValue[1].trim();
        if (value.length() <= 0) {
          log.warn("Ignoring empty value for key '{}' in config for {}", key, settingName);
          continue;
        }
        map.put(key, value);
      }
    }
    return Collections.unmodifiableMap(map);
  }

  @NonNull
  private static Map<String, String> newHashMap(final int size) {
    return new HashMap<>(size + 1, 1f);
  }

  @NonNull
  static BitSet parseIntegerRangeSet(@NonNull String str, final String settingName)
      throws NumberFormatException {
    str = str.replaceAll("\\s", "");
    if (!str.matches("\\d{3}(?:-\\d{3})?(?:,\\d{3}(?:-\\d{3})?)*")) {
      log.warn(
          "Invalid config for {}: '{}'. Must be formatted like '400-403,405,410-499'.",
          settingName,
          str);
      throw new NumberFormatException();
    }

    final int lastSeparator = Math.max(str.lastIndexOf(','), str.lastIndexOf('-'));
    final int maxValue = Integer.parseInt(str.substring(lastSeparator + 1));
    final BitSet set = new BitSet(maxValue);
    final String[] tokens = str.split(",", -1);
    for (final String token : tokens) {
      final int separator = token.indexOf('-');
      if (separator == -1) {
        set.set(Integer.parseInt(token));
      } else if (separator > 0) {
        final int left = Integer.parseInt(token.substring(0, separator));
        final int right = Integer.parseInt(token.substring(separator + 1));
        final int min = Math.min(left, right);
        final int max = Math.max(left, right);
        set.set(min, max + 1);
      }
    }
    return set;
  }
}
