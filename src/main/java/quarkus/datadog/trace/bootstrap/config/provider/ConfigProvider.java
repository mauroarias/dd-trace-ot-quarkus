package quarkus.datadog.trace.bootstrap.config.provider;

import static datadog.trace.api.config.GeneralConfig.CONFIGURATION_FILE;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ConfigProvider {
  protected final ConfigProvider.Source[] sources;

  private ConfigProvider(ConfigProvider.Source... sources) {
    this.sources = sources;
  }

  public final String getString(String key) {
    return getString(key, null);
  }

  public final <T extends Enum<T>> T getEnum(String key, Class<T> enumType, T defaultValue) {
    String value = getString(key);
    if (null != value) {
      try {
        return Enum.valueOf(enumType, value);
      } catch (Exception ignoreAndUseDefault) {
        log.debug("failed to parse {} for {}, defaulting to {}", value, key, defaultValue);
      }
    }
    return defaultValue;
  }

  public final String getString(String key, String defaultValue, String... aliases) {
    for (ConfigProvider.Source source : sources) {
      String value = source.get(key, aliases);
      if (value != null) {
        return value;
      }
    }
    return defaultValue;
  }

  public final String getStringBypassSysProps(String key, String defaultValue) {
    for (ConfigProvider.Source source : sources) {
      if (source instanceof SystemPropertiesConfigSource) {
        continue;
      }
      String value = source.get(key);
      if (value != null) {
        return value;
      }
    }
    return defaultValue;
  }

  public final Boolean getBoolean(String key) {
    final String value = get(key);
    return value == null ? null : new Boolean(value);
  }

  public final boolean getBoolean(String key, boolean defaultValue, String... aliases) {
    final String value = get(key);
    return value == null ? defaultValue : new Boolean(value);
  }

  public final Integer getInteger(String key) {
    final String value = this.get(key);
    return value == null ? null : new Integer(value);
  }

  public final int getInteger(String key, int defaultValue, String... aliases) {
    final String value = this.get(key);
    return value == null ? defaultValue : new Integer(value);
  }

  public final Float getFloat(String key, String... aliases) {
    final String value = this.get(key);
    return value == null ? null : new Float(value);
  }

  public final float getFloat(String key, float defaultValue) {
    final String value = this.get(key);
    return value == null ? defaultValue : new Float(value);
  }

  public final Double getDouble(String key) {
    final String value = this.get(key);
    return value == null ? null : new Double(value);
  }

  public final double getDouble(String key, double defaultValue) {
    final String value = this.get(key);
    return value == null ? defaultValue : new Double(value);
  }

  private String get(String key, String... aliases) {
    for (ConfigProvider.Source source : sources) {
      String value = source.get(key, aliases);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  public final List<String> getList(String key) {
    return ConfigConverter.parseList(getString(key));
  }

  public final Map<String, String> getMergedMap(String key) {
    Map<String, String> merged = new HashMap<>();
    // reverse iterate to allow overrides
    for (int i = sources.length - 1; 0 <= i; i--) {
      String value = sources[i].get(key);
      merged.putAll(ConfigConverter.parseMap(value, key));
    }
    return merged;
  }

  public BitSet getIntegerRange(final String key, final BitSet defaultValue) {
    final String value = getString(key);
    try {
      return value == null ? defaultValue : ConfigConverter.parseIntegerRangeSet(value, key);
    } catch (final NumberFormatException e) {
      log.warn("Invalid configuration for " + key, e);
      return defaultValue;
    }
  }

  public static ConfigProvider createDefault() {
    Properties configProperties =
        loadConfigurationFile(
            new ConfigProvider(new SystemPropertiesConfigSource(), new EnvironmentConfigSource()));
    if (configProperties.isEmpty()) {
      return new ConfigProvider(
          new SystemPropertiesConfigSource(),
          new EnvironmentConfigSource(),
          new CapturedEnvironmentConfigSource());
    } else {
      return new ConfigProvider(
          new SystemPropertiesConfigSource(),
          new EnvironmentConfigSource(),
          new PropertiesConfigSource(configProperties, true),
          new CapturedEnvironmentConfigSource());
    }
  }

  public static ConfigProvider withPropertiesOverride(Properties properties) {
    PropertiesConfigSource providedConfigSource = new PropertiesConfigSource(properties, false);
    Properties configProperties =
        loadConfigurationFile(
            new ConfigProvider(
                new SystemPropertiesConfigSource(),
                new EnvironmentConfigSource(),
                providedConfigSource));
    if (configProperties.isEmpty()) {
      return new ConfigProvider(
          new SystemPropertiesConfigSource(),
          new EnvironmentConfigSource(),
          providedConfigSource,
          new CapturedEnvironmentConfigSource());
    } else {
      return new ConfigProvider(
          providedConfigSource,
          new SystemPropertiesConfigSource(),
          new EnvironmentConfigSource(),
          new PropertiesConfigSource(configProperties, true),
          new CapturedEnvironmentConfigSource());
    }
  }

  /**
   * Loads the optional configuration properties file into the global {@link Properties} object.
   *
   * @return The {@link Properties} object. the returned instance might be empty of file does not
   *     exist or if it is in a wrong format.
   * @param configProvider
   */
  private static Properties loadConfigurationFile(ConfigProvider configProvider) {
    final Properties properties = new Properties();

    // Reading from system property first and from env after
    String configurationFilePath = configProvider.getString(CONFIGURATION_FILE);
    if (null == configurationFilePath) {
      return properties;
    }

    // Normalizing tilde (~) paths for unix systems
    configurationFilePath =
        configurationFilePath.replaceFirst("^~", System.getProperty("user.home"));

    // Configuration properties file is optional
    final File configurationFile = new File(configurationFilePath);
    if (!configurationFile.exists()) {
      log.error("Configuration file '{}' not found.", configurationFilePath);
      return properties;
    }

    try (final FileReader fileReader = new FileReader(configurationFile)) {
      properties.load(fileReader);
    } catch (final FileNotFoundException fnf) {
      log.error("Configuration file '{}' not found.", configurationFilePath);
    } catch (final IOException ioe) {
      log.error(
          "Configuration file '{}' cannot be accessed or correctly parsed.", configurationFilePath);
    }

    return properties;
  }

  public abstract static class Source {
    public final String get(String key, String... aliases) {
      String value = get(key);
      if (value != null) {
        return value;
      }
      for (String alias : aliases) {
        value = get(alias);
        if (value != null) {
          return value;
        }
      }
      return null;
    }

    protected abstract String get(String key);
  }
}
