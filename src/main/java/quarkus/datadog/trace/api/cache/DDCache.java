package quarkus.datadog.trace.api.cache;

import quarkus.datadog.trace.api.Function;

public interface DDCache<K, V> {

  V computeIfAbsent(final K key, Function<K, ? extends V> func);
}
