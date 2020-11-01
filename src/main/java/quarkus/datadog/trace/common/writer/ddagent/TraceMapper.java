package quarkus.datadog.trace.common.writer.ddagent;

import quarkus.datadog.trace.core.DDSpanData;
import quarkus.datadog.trace.core.serialization.msgpack.Mapper;
import java.util.List;

public interface TraceMapper extends Mapper<List<? extends DDSpanData>> {

  Payload newPayload();

  int messageBufferSize();

  void reset();

  String endpoint();
}
