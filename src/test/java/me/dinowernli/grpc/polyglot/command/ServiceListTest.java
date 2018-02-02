package me.dinowernli.grpc.polyglot.command;

import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Any;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import me.dinowernli.grpc.polyglot.protobuf.WellKnownTypes;
import me.dinowernli.grpc.polyglot.testing.RecordingOutput;
import me.dinowernli.junit.TestClass;
import org.junit.Before;
import org.junit.Test;
import polyglot.test.TestProto;
import polyglot.test.foo.FooProto;

import static com.google.common.truth.Truth.assertThat;

/** Unit tests for {@link ServiceList}. */
@TestClass
public class ServiceListTest {
  private static FileDescriptorSet PROTO_FILE_DESCRIPTORS = FileDescriptorSet.newBuilder()
      .addFile(TestProto.getDescriptor().toProto())
      .addFile(FooProto.getDescriptor().toProto())
      .addAllFile(WellKnownTypes.descriptors())
      .build();

  private static final String EXPECTED_SERVICE = "polyglot.test.TestService";
  private static final ImmutableList<String> EXPECTED_METHOD_NAMES = ImmutableList.of(
      "TestMethod", "TestMethodStream", "TestMethodClientStream", "TestMethodBidi");

  private RecordingOutput recordingOutput;

  @Before
  public void setUp() throws Throwable {
    recordingOutput = new RecordingOutput();
  }

  @Test
  public void testServiceListOutput() throws Throwable {
    ServiceList.listServices(
        recordingOutput,
        PROTO_FILE_DESCRIPTORS,
        "",
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
    recordingOutput.close();

    validateOutput(recordingOutput.getContentsAsString(), EXPECTED_SERVICE, EXPECTED_METHOD_NAMES);
  }

  @Test
  public void testServiceListOutputWithServiceFilter() throws Throwable {
    ServiceList.listServices(
        recordingOutput,
        PROTO_FILE_DESCRIPTORS,
        "",
        Optional.of("TestService"),
        Optional.empty(),
        Optional.empty());
    recordingOutput.close();

    validateOutput(recordingOutput.getContentsAsString(), EXPECTED_SERVICE, EXPECTED_METHOD_NAMES);
  }

  @Test
  public void testServiceListOutputWithMethodFilter() throws Throwable {
    ServiceList.listServices(
        recordingOutput,
        PROTO_FILE_DESCRIPTORS,
        "",
        Optional.of("TestService"),
        Optional.of("TestMethodStream"),
        Optional.empty());
    recordingOutput.close();

    validateOutput(
        recordingOutput.getContentsAsString(),
        EXPECTED_SERVICE,
        ImmutableList.of("TestMethodStream"));
  }

  @Test
  public void testServiceListOutputWithMessageDetail() throws Throwable {
    ServiceList.listServices(
        recordingOutput,
        PROTO_FILE_DESCRIPTORS,
        "",
        Optional.of("TestService"),
        Optional.of("TestMethodStream"),
        Optional.of(true));
    recordingOutput.close();

    validateMessageOutput(recordingOutput.getContentsAsString());
  }

  /** Compares the actual output with the expected output format */
  private void validateOutput(
      String output, String serviceName, ImmutableList<String> methodNames) {
    // Assuming no filters, we expect output of the form (note that [tmp_path]
    // is a placeholder):
    //
    // polyglot.test.TestService ->
    // [tmp_path]/src/main/proto/testing/test_service.proto
    // polyglot.test.TestService/TestMethod
    // polyglot.test.TestService/TestMethodStream
    // polyglot.test.TestService/TestMethodBidi

    String[] lines = output.trim().split("\n");
    assertThat(lines.length).isEqualTo(methodNames.size() + 1);

    // Parse the first line (always [ServiceName] -> [FileName]
    assertThat(lines[0]).startsWith(serviceName + " -> ");

    // Parse the subsequent lines (always [ServiceName]/[MethodName])
    for (int i = 0; i < methodNames.size(); i++) {
      assertThat(lines[i + 1].trim()).isEqualTo(serviceName + "/" + methodNames.get(i));
    }
  }

  /** Ensures that the message-rendering logic is correct */
  private void validateMessageOutput(String output) {
    // Assuming the filter is for TestService/TestMethodStream, then the message
    // should render as:
    //
    // polyglot.test.TestService ->
    // [tmp_path]/src/main/proto/testing/test_service.proto
    // polyglot.test.TestService/TestMethodStream
    // message[<optional> <single>]: STRING
    // foo[<optional> <single>] {
    // message[<optional> <single>]: STRING
    // }

    String[] lines = output.trim().split("\n");

    // Parse the first line (always [ServiceName] -> [FileName]
    assertThat(lines[0]).startsWith("polyglot.test.TestService -> ");

    ImmutableList<String> expectedLines = ImmutableList.of(
        "polyglot.test.TestService/TestMethodStream",
        "message[<optional> <single>]: STRING",
        "foo[<optional> <single>] {",
        "message[<optional> <single>]: STRING",
        "}");

    for (int i = 0; i < expectedLines.size(); i++) {
      assertThat(lines[i + 1].trim()).isEqualTo(expectedLines.get(i));
    }
  }
}
