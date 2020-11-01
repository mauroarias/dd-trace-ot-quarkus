package quarkus.datadog.trace.bootstrap.config.provider;

import quarkus.datadog.trace.api.env.CapturedEnvironment;

final class CapturedEnvironmentConfigSource extends ConfigProvider.Source {
  private final CapturedEnvironment env;

  public CapturedEnvironmentConfigSource() {
    this(CapturedEnvironment.get());
  }

  public CapturedEnvironmentConfigSource(CapturedEnvironment env) {
    this.env = env;
  }

  @Override
  protected String get(String key) {
    return env.getProperties().get(key);
  }
}
