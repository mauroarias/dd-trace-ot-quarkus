package quarkus.datadog.opentracing;

import quarkus.datadog.trace.bootstrap.instrumentation.api.AgentPropagation.ContextVisitor;
import quarkus.datadog.trace.bootstrap.instrumentation.api.AgentPropagation.KeyClassifier;
import quarkus.datadog.trace.bootstrap.instrumentation.api.AgentPropagation.Setter;
import quarkus.datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import quarkus.datadog.trace.bootstrap.instrumentation.api.AgentSpan.Context;
import quarkus.datadog.trace.bootstrap.instrumentation.api.AgentTracer;
import quarkus.datadog.trace.bootstrap.instrumentation.api.AgentTracer.TracerAPI;
import quarkus.datadog.trace.core.CoreTracer;
import quarkus.datadog.trace.core.DDSpanContext;
import datadog.trace.api.GlobalTracer;
import datadog.trace.api.interceptor.TraceInterceptor;
import datadog.trace.context.ScopeListener;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtract;
import io.opentracing.propagation.TextMapInject;
import io.opentracing.tag.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class QuarkusDDTracer implements Tracer, datadog.trace.api.Tracer {
    private static final Logger log = LoggerFactory.getLogger(QuarkusDDTracer.class);
    private final TypeConverter converter;
    private final TracerAPI tracer;
    private ScopeManager scopeManager;

    private QuarkusDDTracer() {
        if (GlobalTracer.get().getClass().getName().equals("datadog.lib.datadog.trace.agent.core.CoreTracer")) {
            log.error("Datadog Tracer already installed by `dd-java-agent`. NOTE: Manually creating the tracer while using `dd-java-agent` is not supported");
            throw new IllegalStateException("Datadog Tracer already installed");
        } else {
            this.converter = new TypeConverter(new DefaultLogHandler());

            this.tracer = CoreTracer.builder().build();
            if (scopeManager == null) {
                this.scopeManager = new OTScopeManager(this.tracer, this.converter);
            }

        }
    }

    private static Map<String, String> customRuntimeTags(String runtimeId, Map<String, String> applicationRootSpanTags) {
        Map<String, String> runtimeTags = new HashMap(applicationRootSpanTags);
        runtimeTags.put("runtime-id", runtimeId);
        return Collections.unmodifiableMap(runtimeTags);
    }

    public String getTraceId() {
        return this.tracer.getTraceId();
    }

    public String getSpanId() {
        return this.tracer.getSpanId();
    }

    public boolean addTraceInterceptor(TraceInterceptor traceInterceptor) {
        return this.tracer.addTraceInterceptor(traceInterceptor);
    }

    public void addScopeListener(ScopeListener listener) {
        this.tracer.addScopeListener(listener);
    }

    public ScopeManager scopeManager() {
        return this.scopeManager;
    }

    public Span activeSpan() {
        return this.scopeManager.activeSpan();
    }

    public Scope activateSpan(Span span) {
        return this.scopeManager.activate(span);
    }

    public DDSpanBuilder buildSpan(String operationName) {
        return new DDSpanBuilder(operationName);
    }

    public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
        if (carrier instanceof TextMapInject) {
            Context context = this.converter.toContext(spanContext);
            this.tracer.inject(context, (TextMapInject)carrier, TextMapInjectSetter.INSTANCE);
        } else {
            log.debug("Unsupported format for propagation - {}", format.getClass().getName());
        }

    }

    public <C> SpanContext extract(Format<C> format, C carrier) {
        if (carrier instanceof TextMapExtract) {
            Context tagContext = this.tracer.extract((TextMapExtract)carrier, new TextMapExtractGetter((TextMapExtract)carrier));
            return this.converter.toSpanContext(tagContext);
        } else {
            log.debug("Unsupported format for propagation - {}", format.getClass().getName());
            return null;
        }
    }

    public void close() {
        this.tracer.close();
    }

    public static QuarkusDDTracer build() {
        return new QuarkusDDTracer();
    }

    public class DDSpanBuilder implements SpanBuilder {
        private final AgentTracer.SpanBuilder delegate;

        public DDSpanBuilder(String operationName) {
            this.delegate = QuarkusDDTracer.this.tracer.buildSpan(operationName);
        }

        public DDSpanBuilder asChildOf(SpanContext parent) {
            this.delegate.asChildOf(QuarkusDDTracer.this.converter.toContext(parent));
            return this;
        }

        public DDSpanBuilder asChildOf(Span parent) {
            if (parent != null) {
                this.delegate.asChildOf(QuarkusDDTracer.this.converter.toAgentSpan(parent).context());
            }

            return this;
        }

        public DDSpanBuilder addReference(String referenceType, SpanContext referencedContext) {
            if (referencedContext == null) {
                return this;
            } else {
                Context context = QuarkusDDTracer.this.converter.toContext(referencedContext);
                if (!(context instanceof quarkus.datadog.trace.core.propagation.ExtractedContext) && !(context instanceof DDSpanContext)) {
                    QuarkusDDTracer.log.debug("Expected to have a DDSpanContext or ExtractedContext but got " + context.getClass().getName());
                    return this;
                } else {
                    if (!"child_of".equals(referenceType) && !"follows_from".equals(referenceType)) {
                        QuarkusDDTracer.log.debug("Only support reference type of CHILD_OF and FOLLOWS_FROM");
                    } else {
                        this.delegate.asChildOf(context);
                    }

                    return this;
                }
            }
        }

        public DDSpanBuilder ignoreActiveSpan() {
            this.delegate.ignoreActiveSpan();
            return this;
        }

        public DDSpanBuilder withTag(String key, String value) {
            this.delegate.withTag(key, value);
            return this;
        }

        public DDSpanBuilder withTag(String key, boolean value) {
            this.delegate.withTag(key, value);
            return this;
        }

        public DDSpanBuilder withTag(String key, Number value) {
            this.delegate.withTag(key, value);
            return this;
        }

        public <T> DDSpanBuilder withTag(Tag<T> tag, T value) {
            this.delegate.withTag(tag.getKey(), value);
            return this;
        }

        public DDSpanBuilder withStartTimestamp(long microseconds) {
            this.delegate.withStartTimestamp(microseconds);
            return this;
        }

        public Span startManual() {
            return this.start();
        }

        public Span start() {
            AgentSpan agentSpan = this.delegate.start();
            return QuarkusDDTracer.this.converter.toSpan(agentSpan);
        }

        /** @deprecated */
        @Deprecated
        public Scope startActive(boolean finishSpanOnClose) {
            return QuarkusDDTracer.this.scopeManager.activate(this.start(), finishSpanOnClose);
        }

        public DDSpanBuilder withServiceName(String serviceName) {
            this.delegate.withServiceName(serviceName);
            return this;
        }

        public DDSpanBuilder withResourceName(String resourceName) {
            this.delegate.withResourceName(resourceName);
            return this;
        }

        public DDSpanBuilder withErrorFlag() {
            this.delegate.withErrorFlag();
            return this;
        }

        public DDSpanBuilder withSpanType(String spanType) {
            this.delegate.withSpanType(spanType);
            return this;
        }
    }

    private static class TextMapExtractGetter implements ContextVisitor<TextMapExtract> {
        private final TextMapExtract carrier;

        private TextMapExtractGetter(TextMapExtract carrier) {
            this.carrier = carrier;
        }

        public void forEachKey(TextMapExtract ignored, KeyClassifier classifier) {
            Iterator var3 = this.carrier.iterator();

            Map.Entry entry;
            do {
                if (!var3.hasNext()) {
                    return;
                }

                entry = (Map.Entry)var3.next();
            } while(classifier.accept((String)entry.getKey(), (String)entry.getValue()));

        }
    }

    private static class TextMapInjectSetter implements Setter<TextMapInject> {
        static final TextMapInjectSetter INSTANCE = new TextMapInjectSetter();

        private TextMapInjectSetter() {
        }

        public void set(TextMapInject carrier, String key, String value) {
            carrier.put(key, value);
        }
    }
}
