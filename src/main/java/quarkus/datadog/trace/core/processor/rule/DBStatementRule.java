package quarkus.datadog.trace.core.processor.rule;

import quarkus.datadog.trace.bootstrap.instrumentation.api.Tags;
import quarkus.datadog.trace.core.ExclusiveSpan;
import quarkus.datadog.trace.core.processor.TraceProcessor;

/**
 * Converts db.statement tag to resource name. This is later set to sql.query by the datadog agent
 * after obfuscation.
 */
public class DBStatementRule implements TraceProcessor.Rule {
  @Override
  public String[] aliases() {
    return new String[] {"DBStatementAsResourceName"};
  }

  @Override
  public void processSpan(final ExclusiveSpan span) {
    // Special case: Mongo
    // Skip the decorators
    if (!"java-mongo".equals(span.getTag(Tags.COMPONENT))) {
      final Object dbStatementValue = span.getAndRemoveTag(Tags.DB_STATEMENT);
      if (dbStatementValue instanceof CharSequence) {
        final CharSequence statement = (CharSequence) dbStatementValue;
        if (statement.length() != 0) {
          span.setResourceName(statement);
        }
      }
    }
  }
}
