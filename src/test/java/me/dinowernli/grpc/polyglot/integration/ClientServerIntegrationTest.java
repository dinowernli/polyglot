package me.dinowernli.grpc.polyglot.integration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import me.dinowernli.grpc.polyglot.io.MessageWriter;
import me.dinowernli.grpc.polyglot.testing.RecordingTestService;
import me.dinowernli.grpc.polyglot.testing.TestServer;
import me.dinowernli.grpc.polyglot.testing.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import polyglot.test.TestProto.TestRequest;
import polyglot.test.TestProto.TestResponse;

import static com.google.common.truth.Truth.assertThat;
import static me.dinowernli.grpc.polyglot.testing.TestUtils.makeArgument;

/**
 * An integration test suite which has the Polyglot client talk to a server which records requests.
 */
public class ClientServerIntegrationTest {
  private static final String TEST_UNARY_METHOD = "polyglot.test.TestService/TestMethod";
  private static final String TEST_STREAM_METHOD = "polyglot.test.TestService/TestMethodStream";
  private static final String TEST_CLIENT_STREAM_METHOD =
      "polyglot.test.TestService/TestMethodClientStream";
  private static final String TEST_BIDI_METHOD = "polyglot.test.TestService/TestMethodBidi";

  private static final TestRequest REQUEST = TestRequest.newBuilder()
      .setMessage("i am totally a message")
      .build();

  private TestServer testServer;
  private InputStream storedStdin;
  private Path responseFilePath;

  @Before
  public void setUp() throws Throwable {
    responseFilePath = Files.createTempFile("response", "pb.ascii");
    storedStdin = System.in;
    testServer = TestServer.createAndStart(Optional.empty() /* sslContext */);
  }

  @After
  public void tearDown() throws Throwable {
    testServer.blockingShutdown();
    System.setIn(storedStdin);
    Files.delete(responseFilePath);
  }

  @Test
  public void makesRoundTripUnary() throws Throwable {
    int serverPort = testServer.getGrpcServerPort();
    ImmutableList<String> args = ImmutableList.<String>builder()
        .addAll(makeArgs(serverPort, TestUtils.TESTING_PROTO_ROOT.toString(), TEST_UNARY_METHOD))
        .add(makeArgument("output_file_path", responseFilePath.toString()))
        .build();
    setStdinContents(MessageWriter.writeJsonStream(ImmutableList.of(REQUEST)));

    // Run the full client.
    me.dinowernli.grpc.polyglot.Main.main(args.toArray(new String[0]));

    // Make sure we can parse the response from the file.
    ImmutableList<TestResponse> responses = TestUtils.readResponseFile(responseFilePath);
    assertThat(responses).hasSize(1);
    assertThat(responses.get(0)).isEqualTo(TestServer.UNARY_SERVER_RESPONSE);
  }

  @Test
  public void makesRoundTripServerStream() throws Throwable {
    int serverPort = testServer.getGrpcServerPort();
    ImmutableList<String> args = ImmutableList.<String>builder()
        .addAll(makeArgs(serverPort, TestUtils.TESTING_PROTO_ROOT.toString(), TEST_STREAM_METHOD))
        .add(makeArgument("output_file_path", responseFilePath.toString()))
        .build();
    setStdinContents(MessageWriter.writeJsonStream(ImmutableList.of(REQUEST)));

    // Run the full client.
    me.dinowernli.grpc.polyglot.Main.main(args.toArray(new String[0]));

    // Make sure we can parse the response from the file.
    ImmutableList<TestResponse> responses = TestUtils.readResponseFile(responseFilePath);
    assertThat(responses).containsExactly(TestServer.STREAMING_SERVER_RESPONSE);
  }

  @Test
  public void makesRoundTripClientStream() throws Throwable {
    int serverPort = testServer.getGrpcServerPort();
    ImmutableList<String> args = ImmutableList.<String>builder()
        .addAll(makeArgs(
            serverPort, TestUtils.TESTING_PROTO_ROOT.toString(), TEST_CLIENT_STREAM_METHOD))
        .add(makeArgument("output_file_path", responseFilePath.toString()))
        .build();
    setStdinContents(MessageWriter.writeJsonStream(ImmutableList.of(REQUEST, REQUEST, REQUEST)));

    // Run the full client.
    me.dinowernli.grpc.polyglot.Main.main(args.toArray(new String[0]));

    // Make sure we can parse the response from the file.
    ImmutableList<TestResponse> responses = TestUtils.readResponseFile(responseFilePath);
    assertThat(responses).containsExactly(TestServer.CLIENT_STREAMING_SERVER_RESPONSE);
  }

  @Test
  public void makesRoundTripBidiStream() throws Throwable {
    int serverPort = testServer.getGrpcServerPort();
    ImmutableList<String> args = ImmutableList.<String>builder()
        .addAll(makeArgs(
            serverPort, TestUtils.TESTING_PROTO_ROOT.toString(), TEST_BIDI_METHOD))
        .add(makeArgument("output_file_path", responseFilePath.toString()))
        .build();
    setStdinContents(MessageWriter.writeJsonStream(ImmutableList.of(REQUEST, REQUEST, REQUEST)));

    // Run the full client.
    me.dinowernli.grpc.polyglot.Main.main(args.toArray(new String[0]));

    // Make sure we can parse the response from the file.
    ImmutableList<TestResponse> responses = TestUtils.readResponseFile(responseFilePath);
    assertThat(responses).containsExactly(
        TestServer.BIDI_SERVER_RESPONSE,
        TestServer.BIDI_SERVER_RESPONSE,
        TestServer.BIDI_SERVER_RESPONSE);
  }

  @Test(expected = RuntimeException.class)
  public void rejectsBadInput() throws Throwable {
    ImmutableList<String> args = makeArgs(
        testServer.getGrpcServerPort(), TestUtils.TESTING_PROTO_ROOT.toString(), TEST_UNARY_METHOD);
    setStdinContents("this is not a valid text proto!");

    // Run the full client.
    me.dinowernli.grpc.polyglot.Main.main(args.toArray(new String[0]));

    RecordingTestService recordingTestService = testServer.getServiceImpl();
    assertThat(recordingTestService.numRequests()).isEqualTo(1);
    assertThat(recordingTestService.getRequest(0)).isEqualTo(REQUEST);
  }

  private static ImmutableList<String> makeArgs(int port, String protoRoot, String method) {
    return TestUtils.makePolyglotCallArgs(
        Joiner.on(':').join("localhost", port), protoRoot, method);
  }

  private static void setStdinContents(String contents) {
    System.setIn(new ByteArrayInputStream(contents.getBytes(Charsets.UTF_8)));
  }
}
