package quarkus.datadog.trace.core.processor.rule;

import quarkus.datadog.trace.bootstrap.instrumentation.api.InstrumentationTags;
import quarkus.datadog.trace.core.ExclusiveSpan;
import quarkus.datadog.trace.core.processor.TraceProcessor;

public class MarkSpanForMetricCalculationRule implements TraceProcessor.Rule {
  @Override
  public String[] aliases() {
    return new String[] {};
  }

  @Override
  public void processSpan(final ExclusiveSpan span) {
    final Object val = span.getAndRemoveTag(InstrumentationTags.DD_MEASURED);
    if (val instanceof Boolean && (Boolean) val) {
      span.setMetric(InstrumentationTags.DD_MEASURED, 1);
    }
  }
}
