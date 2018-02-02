package me.dinowernli.grpc.polyglot.io;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.TypeRegistry;
import me.dinowernli.junit.TestClass;
import me.dinowernli.grpc.polyglot.io.testing.TestData;
import me.dinowernli.grpc.polyglot.testing.TestUtils;
import org.junit.Test;
import polyglot.test.TestProto.TestRequest;
import polyglot.test.TestProto.TestResponse;
import polyglot.test.TestProto.TunnelMessage;

import static com.google.common.truth.Truth.assertThat;

/** Unit tests for {@link MessageReader}. */
@TestClass
public class MessageReaderTest {
  private static final String SOURCE = "TEST_SOURCE";
  private static final Descriptor DESCRIPTOR = TestRequest.getDescriptor();
  private static String TESTDATA_ROOT = Paths.get(TestUtils.getWorkspaceRoot().toString(),
      "src", "test", "java", "me", "dinowernli", "grpc", "polyglot", "io", "testdata").toString();

  private MessageReader reader;

  @Test
  public void readsSingle() {
    reader = MessageReader.forFile(dataFilePath("request.pb.ascii"), DESCRIPTOR);
    ImmutableList<DynamicMessage> result = reader.read();
    assertThat(result).containsExactly(TestData.REQUEST);
  }

  @Test
  public void readsMultiple() {
    reader = MessageReader.forFile(dataFilePath("requests_multi.pb.ascii"), DESCRIPTOR);
    ImmutableList<DynamicMessage> result = reader.read();
    assertThat(result).containsExactlyElementsIn(TestData.REQUESTS_MULTI);
  }

  @Test
  public void readsMultipleWithInterruption() {
    reader = MessageReader.forFile(dataFilePath("requests_multi_interrupted.pb.ascii"), DESCRIPTOR);
    ImmutableList<DynamicMessage> result = reader.read();
    assertThat(result).containsExactlyElementsIn(TestData.REQUESTS_MULTI);
  }

  @Test
  public void handlesEmptyStream() {
    reader = MessageReader.forFile(dataFilePath("request_empty.pb.ascii"), DESCRIPTOR);
    ImmutableList<DynamicMessage> result = reader.read();
    assertThat(result).isEmpty();
  }

  @Test
  public void handlesPrimitives() {
    reader = MessageReader.forFile(dataFilePath("request_with_primitives.pb.ascii"), DESCRIPTOR);
    ImmutableList<DynamicMessage> result = reader.read();
    assertThat(result).containsExactly(TestData.REQUEST_WITH_PRIMITIVE);
  }

  @Test
  public void handlesAnyType() {
    TypeRegistry registry = TypeRegistry.newBuilder()
        .add(TunnelMessage.getDescriptor())
        .build();
    reader = MessageReader.forFile(
        dataFilePath("response_any.pb.ascii"), TestResponse.getDescriptor(), registry);
    ImmutableList<DynamicMessage> result = reader.read();

    // If this doesn't crash, then we're happy.
    assertThat(result).hasSize(1);
  }

  @Test
  public void handlesEmptyString() {
    reader = createWithInput("");
    ImmutableList<DynamicMessage> result = reader.read();
    assertThat(result).isEmpty();
  }

  @Test
  public void handlesSingleNewline() {
    reader = createWithInput("\n");
    ImmutableList<DynamicMessage> result = reader.read();
    assertThat(result).isEmpty();
  }

  @Test
  public void handlesTwoNewlines() {
    reader = createWithInput("\n\n");
    ImmutableList<DynamicMessage> result = reader.read();
    assertThat(result).isEmpty();
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsMalformed() {
    reader = createWithInput("ha! try parsing this as a stream");
    ImmutableList<DynamicMessage> result = reader.read();
    assertThat(result).isEmpty();
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsBadProto() {
    reader = createWithInput("{ 'message': 'as \n\n");
    ImmutableList<DynamicMessage> result = reader.read();
    assertThat(result).isEmpty();
  }

  private static Path dataFilePath(String filename) {
    return Paths.get(TESTDATA_ROOT, filename);
  }

  private static MessageReader createWithInput(String input) {
    return new MessageReader(
        JsonFormat.parser(),
        TestRequest.getDescriptor(),
        new BufferedReader(new StringReader(input)),
        SOURCE);
  }
}
