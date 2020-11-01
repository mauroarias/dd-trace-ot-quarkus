package quarkus.datadog.trace.core.processor.rule;

import static datadog.trace.api.DDSpanTypes.HTTP_CLIENT;
import static datadog.trace.api.DDSpanTypes.HTTP_SERVER;

import quarkus.datadog.trace.api.Config;
import quarkus.datadog.trace.core.ExclusiveSpan;
import quarkus.datadog.trace.core.processor.TraceProcessor;
import java.util.BitSet;

public class HttpStatusErrorRule implements TraceProcessor.Rule {

  private final BitSet serverErrorStatuses = Config.get().getHttpServerErrorStatuses();
  private final BitSet clientErrorStatuses = Config.get().getHttpClientErrorStatuses();

  @Override
  public String[] aliases() {
    return new String[] {};
  }

  @Override
  public void processSpan(ExclusiveSpan span) {
    if (!span.isError()) {
      CharSequence spanType = span.getType();
      if (null != spanType) {
        switch (spanType.toString()) {
          case HTTP_SERVER:
            span.setError(serverErrorStatuses.get(span.getHttpStatus()));
            break;
          case HTTP_CLIENT:
            span.setError(clientErrorStatuses.get(span.getHttpStatus()));
            break;
          default:
        }
      }
    }
  }
}
