package quarkus.datadog.opentracing.resolver;

import quarkus.datadog.opentracing.DDTracer;
import quarkus.datadog.trace.api.Config;
import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DDTracerResolver extends TracerResolver {

  Tracer resolve(final Config config) {
    if (config.isTraceResolverEnabled()) {
      log.info("Creating DDTracer with DDTracerResolver");
      return DDTracer.builder().config(config).build();
    } else {
      log.info("DDTracerResolver disabled");
      return null;
    }
  }

  @Override
  protected Tracer resolve() {
    return resolve(Config.get());
  }
}
