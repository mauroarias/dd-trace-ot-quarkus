package quarkus.datadog.trace.common.sampling;

public interface RateSampler extends Sampler {
  double getSampleRate();
}
