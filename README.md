# Datadog DD-trace-ot with Quarkus native Support

This library is a fork of the original library DD-trace-java https://github.com/DataDog/dd-trace-java with some modifications to support Quarkus. Please refer to the original license before modifying or forking this repository https://github.com/DataDog/dd-trace-java/blob/master/LICENSE

# Compiling & maven locally deploy

mvn clean package install

# How use in quarkus project

add dependency libraries:

        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-smallrye-opentracing</artifactId>
        </dependency>
        <dependency>
            <groupId>com.datadoghq</groupId>
            <artifactId>dd-trace-ot-quarkus</artifactId>
            <version>${dd-trace-java.version}</version>
        </dependency>
        <dependency>
            <groupId>io.opentracing</groupId>
            <artifactId>opentracing-api</artifactId>
            <version>${opentracing.api.version}</version>
        </dependency>
        
create Datadog config injection, something like this:

    @Startup
    @ApplicationScoped
    public class DatadogConfig {
    
        private static final Logger log = Logger.getLogger(DatadogConfig.class);
    
        @PostConstruct
        void startup() {
            log.info("post constructor");
            configDD();
        }
    
        public void configDD() {
            QuarkusDDTracer tracer = QuarkusDDTracer.build();
            GlobalTracer.register(tracer);
            datadog.trace.api.GlobalTracer.registerIfAbsent(tracer);
        }
    }
    
define your configuration using environment vars, check config options in datadog docs: https://docs.datadoghq.com/tracing/setup/java/?tab=springboot

Note that must add quarkus dependency "quarkus-smallrye-opentracing" but it CANNOT be activated using Quarkus... More details in https://github.com/mauroarias/template-quarkus-app/tree/datadog 