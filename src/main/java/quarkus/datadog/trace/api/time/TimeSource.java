package quarkus.datadog.trace.api.time;

public interface TimeSource {
  long getNanoTime();
}
