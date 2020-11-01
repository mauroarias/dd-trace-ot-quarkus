package quarkus.datadog.trace.common.sampling;

import quarkus.datadog.trace.core.DDSpan;

public interface PrioritySampler {
  void setSamplingPriority(DDSpan span);
}
