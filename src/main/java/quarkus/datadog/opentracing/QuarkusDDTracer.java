package quarkus.datadog.opentracing;

import datadog.trace.api.DDTags;
import datadog.trace.api.GlobalTracer;
import datadog.trace.api.interceptor.TraceInterceptor;
import quarkus.datadog.trace.api.Config;
import quarkus.datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import quarkus.datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import quarkus.datadog.trace.bootstrap.instrumentation.api.AgentTracer;
import datadog.trace.context.ScopeListener;
import quarkus.datadog.trace.core.CoreTracer;
import quarkus.datadog.trace.core.DDSpanContext;
import quarkus.datadog.trace.core.propagation.ExtractedContext;
import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtract;
import io.opentracing.propagation.TextMapInject;
import io.opentracing.tag.Tag;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import lombok.extern.slf4j.Slf4j;

/**
 * DDTracer implements the <code>io.opentracing.Tracer</code> interface to make it easy to send
 * traces and spans to Datadog using the OpenTracing API.
 */
@Slf4j
public class QuarkusDDTracer implements Tracer, datadog.trace.api.Tracer {
  private final TypeConverter converter;
  private final AgentTracer.TracerAPI tracer;

  // FIXME [API] There's an unfortunate cycle between OTScopeManager and CoreTracer where they
  // each depend on each other so scopeManager can't be final
  // Perhaps the api can change so that CoreTracer doesn't need to implement scope methods directly
  private ScopeManager scopeManager;

  //prameters must be defined as env vars
  private QuarkusDDTracer() {

    // Check if the tracer is already installed by the agent
    // Unable to use "instanceof" because of class renaming
    if (GlobalTracer.get().getClass().getName().equals("quarkus.datadog.trace.agent.core.CoreTracer")) {
      log.error(
          "Datadog Tracer already installed by `dd-java-agent`. NOTE: Manually creating the tracer while using `dd-java-agent` is not supported");
      throw new IllegalStateException("Datadog Tracer already installed");
    } else {
      converter = new TypeConverter(new DefaultLogHandler());

      tracer = CoreTracer.builder().build();
      if (scopeManager == null) {
        this.scopeManager = new OTScopeManager(this.tracer, this.converter);
      }
    }
  }

  private static Map<String, String> customRuntimeTags(
      final String runtimeId, final Map<String, String> applicationRootSpanTags) {
    final Map<String, String> runtimeTags = new HashMap<>(applicationRootSpanTags);
    runtimeTags.put(DDTags.RUNTIME_ID_TAG, runtimeId);
    return Collections.unmodifiableMap(runtimeTags);
  }

  @Override
  public String getTraceId() {
    return tracer.getTraceId();
  }

  @Override
  public String getSpanId() {
    return tracer.getSpanId();
  }

  @Override
  public boolean addTraceInterceptor(final TraceInterceptor traceInterceptor) {
    return tracer.addTraceInterceptor(traceInterceptor);
  }

  @Override
  public void addScopeListener(final ScopeListener listener) {
    tracer.addScopeListener(listener);
  }

  @Override
  public ScopeManager scopeManager() {
    return scopeManager;
  }

  @Override
  public Span activeSpan() {
    return scopeManager.activeSpan();
  }

  @Override
  public Scope activateSpan(final Span span) {
    return scopeManager.activate(span);
  }

  @Override
  public DDSpanBuilder buildSpan(final String operationName) {
    return new DDSpanBuilder(operationName);
  }

  @Override
  public <C> void inject(final SpanContext spanContext, final Format<C> format, final C carrier) {
    if (carrier instanceof TextMapInject) {
      final AgentSpan.Context context = converter.toContext(spanContext);

      tracer.inject(context, (TextMapInject) carrier, TextMapInjectSetter.INSTANCE);
    } else {
      log.debug("Unsupported format for propagation - {}", format.getClass().getName());
    }
  }

  @Override
  public <C> SpanContext extract(final Format<C> format, final C carrier) {
    if (carrier instanceof TextMapExtract) {
      final AgentSpan.Context tagContext =
          tracer.extract(
              (TextMapExtract) carrier, new TextMapExtractGetter((TextMapExtract) carrier));

      return converter.toSpanContext(tagContext);
    } else {
      log.debug("Unsupported format for propagation - {}", format.getClass().getName());
      return null;
    }
  }

  @Override
  public void close() {
    tracer.close();
  }

  public static QuarkusDDTracer build() {
    return new QuarkusDDTracer();
  }

  private static class TextMapInjectSetter implements AgentPropagation.Setter<TextMapInject> {
    static final TextMapInjectSetter INSTANCE = new TextMapInjectSetter();

    @Override
    public void set(final TextMapInject carrier, final String key, final String value) {
      carrier.put(key, value);
    }
  }

  private static class TextMapExtractGetter
      implements AgentPropagation.ContextVisitor<TextMapExtract> {
    private final TextMapExtract carrier;

    private TextMapExtractGetter(final TextMapExtract carrier) {
      this.carrier = carrier;
    }

    @Override
    public void forEachKey(TextMapExtract ignored, AgentPropagation.KeyClassifier classifier) {
      for (Entry<String, String> entry : carrier) {
        if (!classifier.accept(entry.getKey(), entry.getValue())) {
          return;
        }
      }
    }
  }

  public class DDSpanBuilder implements SpanBuilder {
    private final AgentTracer.SpanBuilder delegate;

    public DDSpanBuilder(final String operationName) {
      delegate = tracer.buildSpan(operationName);
    }

    @Override
    public DDSpanBuilder asChildOf(final SpanContext parent) {
      delegate.asChildOf(converter.toContext(parent));
      return this;
    }

    @Override
    public DDSpanBuilder asChildOf(final Span parent) {
      if (parent != null) {
        delegate.asChildOf(converter.toAgentSpan(parent).context());
      }
      return this;
    }

    @Override
    public DDSpanBuilder addReference(
        final String referenceType, final SpanContext referencedContext) {
      if (referencedContext == null) {
        return this;
      }

      final AgentSpan.Context context = converter.toContext(referencedContext);
      if (!(context instanceof ExtractedContext) && !(context instanceof DDSpanContext)) {
        log.debug(
            "Expected to have a DDSpanContext or ExtractedContext but got "
                + context.getClass().getName());
        return this;
      }

      if (References.CHILD_OF.equals(referenceType)
          || References.FOLLOWS_FROM.equals(referenceType)) {
        delegate.asChildOf(context);
      } else {
        log.debug("Only support reference type of CHILD_OF and FOLLOWS_FROM");
      }

      return this;
    }

    @Override
    public DDSpanBuilder ignoreActiveSpan() {
      delegate.ignoreActiveSpan();
      return this;
    }

    @Override
    public DDSpanBuilder withTag(final String key, final String value) {
      delegate.withTag(key, value);
      return this;
    }

    @Override
    public DDSpanBuilder withTag(final String key, final boolean value) {
      delegate.withTag(key, value);
      return this;
    }

    @Override
    public DDSpanBuilder withTag(final String key, final Number value) {
      delegate.withTag(key, value);
      return this;
    }

    @Override
    public <T> DDSpanBuilder withTag(final Tag<T> tag, final T value) {
      delegate.withTag(tag.getKey(), value);
      return this;
    }

    @Override
    public DDSpanBuilder withStartTimestamp(final long microseconds) {
      delegate.withStartTimestamp(microseconds);
      return this;
    }

    @Override
    public Span startManual() {
      return start();
    }

    @Override
    public Span start() {
      final AgentSpan agentSpan = delegate.start();
      return converter.toSpan(agentSpan);
    }

    /** @deprecated use {@link #start()} instead. */
    @Deprecated
    @Override
    public Scope startActive(final boolean finishSpanOnClose) {
      return scopeManager.activate(start(), finishSpanOnClose);
    }

    public DDSpanBuilder withServiceName(final String serviceName) {
      delegate.withServiceName(serviceName);
      return this;
    }

    public DDSpanBuilder withResourceName(final String resourceName) {
      delegate.withResourceName(resourceName);
      return this;
    }

    public DDSpanBuilder withErrorFlag() {
      delegate.withErrorFlag();
      return this;
    }

    public DDSpanBuilder withSpanType(final String spanType) {
      delegate.withSpanType(spanType);
      return this;
    }
  }
}
