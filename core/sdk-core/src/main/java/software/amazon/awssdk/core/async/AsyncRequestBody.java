/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.core.async;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import software.amazon.awssdk.annotations.SdkPublicApi;
import software.amazon.awssdk.core.internal.async.ByteBuffersAsyncRequestBody;
import software.amazon.awssdk.core.internal.async.FileAsyncRequestBody;
import software.amazon.awssdk.core.internal.async.InputStreamWithExecutorAsyncRequestBody;
import software.amazon.awssdk.core.internal.async.SplittingPublisher;
import software.amazon.awssdk.core.internal.util.Mimetype;
import software.amazon.awssdk.utils.BinaryUtils;
import software.amazon.awssdk.utils.Validate;

/**
 * Interface to allow non-blocking streaming of request content. This follows the reactive streams pattern where this interface is
 * the {@link Publisher} of data (specifically {@link ByteBuffer} chunks) and the HTTP client is the Subscriber of the data (i.e.
 * to write that data on the wire).
 *
 * <p>
 * {@link #subscribe(Subscriber)} should be implemented to tie this publisher to a subscriber. Ideally each call to subscribe
 * should reproduce the content (i.e if you are reading from a file each subscribe call should produce a
 * {@link org.reactivestreams.Subscription} that reads the file fully). This allows for automatic retries to be performed in the
 * SDK. If the content is not reproducible,  an exception may be thrown from any subsequent {@link #subscribe(Subscriber)} calls.
 * </p>
 *
 * <p>
 * It is important to only send the number of chunks that the subscriber requests to avoid out of memory situations. The
 * subscriber does it's own buffering so it's usually not needed to buffer in the publisher. Additional permits for chunks will be
 * notified via the {@link org.reactivestreams.Subscription#request(long)} method.
 * </p>
 *
 * @see FileAsyncRequestBody
 * @see ByteBuffersAsyncRequestBody
 */
@SdkPublicApi
public interface AsyncRequestBody extends SdkPublisher<ByteBuffer> {

    /**
     * @return The content length of the data being produced.
     */
    Optional<Long> contentLength();

    /**
     * @return The content type of the data being produced.
     */
    default String contentType() {
        return Mimetype.MIMETYPE_OCTET_STREAM;
    }

    /**
     * Creates an {@link AsyncRequestBody} the produces data from the input ByteBuffer publisher. The data is delivered when the
     * publisher publishes the data.
     *
     * @param publisher Publisher of source data
     * @return Implementation of {@link AsyncRequestBody} that produces data send by the publisher
     */
    static AsyncRequestBody fromPublisher(Publisher<ByteBuffer> publisher) {
        return new AsyncRequestBody() {

            /**
             * Returns empty optional as size of the each bytebuffer sent is unknown
             */
            @Override
            public Optional<Long> contentLength() {
                return Optional.empty();
            }

            @Override
            public void subscribe(Subscriber<? super ByteBuffer> s) {
                publisher.subscribe(s);
            }
        };
    }

    /**
     * Creates an {@link AsyncRequestBody} that produces data from the contents of a file. See
     * {@link FileAsyncRequestBody#builder} to create a customized body implementation.
     *
     * @param path Path to file to read from.
     * @return Implementation of {@link AsyncRequestBody} that reads data from the specified file.
     * @see FileAsyncRequestBody
     */
    static AsyncRequestBody fromFile(Path path) {
        return FileAsyncRequestBody.builder().path(path).build();
    }

    /**
     * Creates an {@link AsyncRequestBody} that produces data from the contents of a file. See
     * {@link FileAsyncRequestBody#builder} to create a customized body implementation.
     *
     * @param file The file to read from.
     * @return Implementation of {@link AsyncRequestBody} that reads data from the specified file.
     * @see FileAsyncRequestBody
     */
    static AsyncRequestBody fromFile(File file) {
        return FileAsyncRequestBody.builder().path(file.toPath()).build();
    }

    /**
     * Creates an {@link AsyncRequestBody} that uses a single string as data.
     *
     * @param string The string to provide.
     * @param cs The {@link Charset} to use.
     * @return Implementation of {@link AsyncRequestBody} that uses the specified string.
     * @see ByteBuffersAsyncRequestBody
     */
    static AsyncRequestBody fromString(String string, Charset cs) {
        return ByteBuffersAsyncRequestBody.from(Mimetype.MIMETYPE_TEXT_PLAIN + "; charset=" + cs.name(),
                                                string.getBytes(cs));
    }

    /**
     * Creates an {@link AsyncRequestBody} that uses a single string as data with UTF_8 encoding.
     *
     * @param string The string to send.
     * @return Implementation of {@link AsyncRequestBody} that uses the specified string.
     * @see #fromString(String, Charset)
     */
    static AsyncRequestBody fromString(String string) {
        return fromString(string, StandardCharsets.UTF_8);
    }

    /**
     * Creates an {@link AsyncRequestBody} from a byte array. This will copy the contents of the byte array to prevent
     * modifications to the provided byte array from being reflected in the {@link AsyncRequestBody}.
     *
     * @param bytes The bytes to send to the service.
     * @return AsyncRequestBody instance.
     */
    static AsyncRequestBody fromBytes(byte[] bytes) {
        byte[] clonedBytes = bytes.clone();
        return ByteBuffersAsyncRequestBody.from(clonedBytes);
    }

    /**
     * Creates an {@link AsyncRequestBody} from a byte array <b>without</b> copying the contents of the byte array. This
     * introduces concurrency risks, allowing: (1) the caller to modify the byte array stored in this {@code AsyncRequestBody}
     * implementation AND (2) any users of {@link #fromBytesUnsafe(byte[])} to modify the byte array passed into this
     * {@code AsyncRequestBody} implementation.
     *
     * <p>As the method name implies, this is unsafe. Use {@link #fromBytes(byte[])} unless you're sure you know the risks.
     *
     * @param bytes The bytes to send to the service.
     * @return AsyncRequestBody instance.
     */
    static AsyncRequestBody fromBytesUnsafe(byte[] bytes) {
        return ByteBuffersAsyncRequestBody.from(bytes);
    }

    /**
     * Creates an {@link AsyncRequestBody} from a {@link ByteBuffer}. This will copy the contents of the {@link ByteBuffer} to
     * prevent modifications to the provided {@link ByteBuffer} from being reflected in the {@link AsyncRequestBody}.
     * <p>
     * <b>NOTE:</b> This method ignores the current read position. Use {@link #fromRemainingByteBuffer(ByteBuffer)} if you need
     * it to copy only the remaining readable bytes.
     *
     * @param byteBuffer ByteBuffer to send to the service.
     * @return AsyncRequestBody instance.
     */
    static AsyncRequestBody fromByteBuffer(ByteBuffer byteBuffer) {
        ByteBuffer immutableCopy = BinaryUtils.immutableCopyOf(byteBuffer);
        immutableCopy.rewind();
        return ByteBuffersAsyncRequestBody.of((long) immutableCopy.remaining(), immutableCopy);
    }

    /**
     * Creates an {@link AsyncRequestBody} from the remaining readable bytes from a {@link ByteBuffer}. This will copy the
     * remaining contents of the {@link ByteBuffer} to prevent modifications to the provided {@link ByteBuffer} from being
     * reflected in the {@link AsyncRequestBody}.
     * <p> Unlike {@link #fromByteBuffer(ByteBuffer)}, this method respects the current read position of the buffer and reads
     * only the remaining bytes.
     *
     * @param byteBuffer ByteBuffer to send to the service.
     * @return AsyncRequestBody instance.
     */
    static AsyncRequestBody fromRemainingByteBuffer(ByteBuffer byteBuffer) {
        ByteBuffer immutableCopy = BinaryUtils.immutableCopyOfRemaining(byteBuffer);
        return ByteBuffersAsyncRequestBody.of((long) immutableCopy.remaining(), immutableCopy);
    }

    /**
     * Creates an {@link AsyncRequestBody} from a {@link ByteBuffer} <b>without</b> copying the contents of the
     * {@link ByteBuffer}. This introduces concurrency risks, allowing the caller to modify the {@link ByteBuffer} stored in this
     * {@code AsyncRequestBody} implementation.
     * <p>
     * <b>NOTE:</b> This method ignores the current read position. Use {@link #fromRemainingByteBufferUnsafe(ByteBuffer)} if you
     * need it to copy only the remaining readable bytes.
     *
     * <p>As the method name implies, this is unsafe. Use {@link #fromByteBuffer(ByteBuffer)}} unless you're sure you know the
     * risks.
     *
     * @param byteBuffer ByteBuffer to send to the service.
     * @return AsyncRequestBody instance.
     */
    static AsyncRequestBody fromByteBufferUnsafe(ByteBuffer byteBuffer) {
        ByteBuffer readOnlyBuffer = byteBuffer.asReadOnlyBuffer();
        readOnlyBuffer.rewind();
        return ByteBuffersAsyncRequestBody.of((long) readOnlyBuffer.remaining(), readOnlyBuffer);
    }

    /**
     * Creates an {@link AsyncRequestBody} from a {@link ByteBuffer} <b>without</b> copying the contents of the
     * {@link ByteBuffer}. This introduces concurrency risks, allowing the caller to modify the {@link ByteBuffer} stored in this
     * {@code AsyncRequestBody} implementation.
     * <p>Unlike {@link #fromByteBufferUnsafe(ByteBuffer)}, this method respects the current read position of
     * the buffer and reads only the remaining bytes.
     *
     * <p>As the method name implies, this is unsafe. Use {@link #fromByteBuffer(ByteBuffer)}} unless you're sure you know the
     * risks.
     *
     * @param byteBuffer ByteBuffer to send to the service.
     * @return AsyncRequestBody instance.
     */
    static AsyncRequestBody fromRemainingByteBufferUnsafe(ByteBuffer byteBuffer) {
        ByteBuffer readOnlyBuffer = byteBuffer.asReadOnlyBuffer();
        return ByteBuffersAsyncRequestBody.of((long) readOnlyBuffer.remaining(), readOnlyBuffer);
    }

    /**
     * Creates an {@link AsyncRequestBody} from a {@link ByteBuffer} array. This will copy the contents of each {@link ByteBuffer}
     * to prevent modifications to any provided {@link ByteBuffer} from being reflected in the {@link AsyncRequestBody}.
     * <p>
     * <b>NOTE:</b> This method ignores the current read position of each {@link ByteBuffer}. Use
     * {@link #fromRemainingByteBuffers(ByteBuffer...)} if you need it to copy only the remaining readable bytes.
     *
     * @param byteBuffers ByteBuffer array to send to the service.
     * @return AsyncRequestBody instance.
     */
    static AsyncRequestBody fromByteBuffers(ByteBuffer... byteBuffers) {
        ByteBuffer[] immutableCopy = Arrays.stream(byteBuffers)
                                           .map(BinaryUtils::immutableCopyOf)
                                           .peek(ByteBuffer::rewind)
                                           .toArray(ByteBuffer[]::new);
        return ByteBuffersAsyncRequestBody.of(immutableCopy);
    }

    /**
     * Creates an {@link AsyncRequestBody} from a {@link ByteBuffer} array. This will copy the remaining contents of each
     * {@link ByteBuffer} to prevent modifications to any provided {@link ByteBuffer} from being reflected in the
     * {@link AsyncRequestBody}.
     * <p>Unlike {@link #fromByteBufferUnsafe(ByteBuffer)},
     * this method respects the current read position of each buffer and reads only the remaining bytes.
     *
     * @param byteBuffers ByteBuffer array to send to the service.
     * @return AsyncRequestBody instance.
     */
    static AsyncRequestBody fromRemainingByteBuffers(ByteBuffer... byteBuffers) {
        ByteBuffer[] immutableCopy = Arrays.stream(byteBuffers)
                                           .map(BinaryUtils::immutableCopyOfRemaining)
                                           .peek(ByteBuffer::rewind)
                                           .toArray(ByteBuffer[]::new);
        return ByteBuffersAsyncRequestBody.of(immutableCopy);
    }

    /**
     * Creates an {@link AsyncRequestBody} from a {@link ByteBuffer} array <b>without</b> copying the contents of each
     * {@link ByteBuffer}. This introduces concurrency risks, allowing the caller to modify any {@link ByteBuffer} stored in this
     * {@code AsyncRequestBody} implementation.
     * <p>
     * <b>NOTE:</b> This method ignores the current read position of each {@link ByteBuffer}. Use
     * {@link #fromRemainingByteBuffers(ByteBuffer...)} if you need it to copy only the remaining readable bytes.
     *
     * <p>As the method name implies, this is unsafe. Use {@link #fromByteBuffers(ByteBuffer...)} unless you're sure you know the
     * risks.
     *
     * @param byteBuffers ByteBuffer array to send to the service.
     * @return AsyncRequestBody instance.
     */
    static AsyncRequestBody fromByteBuffersUnsafe(ByteBuffer... byteBuffers) {
        ByteBuffer[] readOnlyBuffers = Arrays.stream(byteBuffers)
                                             .map(ByteBuffer::asReadOnlyBuffer)
                                             .peek(ByteBuffer::rewind)
                                             .toArray(ByteBuffer[]::new);
        return ByteBuffersAsyncRequestBody.of(readOnlyBuffers);
    }

    /**
     * Creates an {@link AsyncRequestBody} from a {@link ByteBuffer} array <b>without</b> copying the contents of each
     * {@link ByteBuffer}. This introduces concurrency risks, allowing the caller to modify any {@link ByteBuffer} stored in this
     * {@code AsyncRequestBody} implementation.
     * <p>Unlike {@link #fromByteBuffersUnsafe(ByteBuffer...)},
     * this method respects the current read position of each buffer and reads only the remaining bytes.
     *
     * <p>As the method name implies, this is unsafe. Use {@link #fromByteBuffers(ByteBuffer...)} unless you're sure you know the
     * risks.
     *
     * @param byteBuffers ByteBuffer array to send to the service.
     * @return AsyncRequestBody instance.
     */
    static AsyncRequestBody fromRemainingByteBuffersUnsafe(ByteBuffer... byteBuffers) {
        ByteBuffer[] readOnlyBuffers = Arrays.stream(byteBuffers)
                                             .map(ByteBuffer::asReadOnlyBuffer)
                                             .toArray(ByteBuffer[]::new);
        return ByteBuffersAsyncRequestBody.of(readOnlyBuffers);
    }

    /**
     * Creates an {@link AsyncRequestBody} from an {@link InputStream}.
     *
     * <p>An {@link ExecutorService} is required in order to perform the blocking data reads, to prevent blocking the
     * non-blocking event loop threads owned by the SDK.
     */
    static AsyncRequestBody fromInputStream(InputStream inputStream, Long contentLength, ExecutorService executor) {
        return new InputStreamWithExecutorAsyncRequestBody(inputStream, contentLength, executor);
    }

    /**
     * Creates a {@link BlockingInputStreamAsyncRequestBody} to use for writing an input stream to the downstream service.
     *
     * <p><b>Example Usage</b>
     *
     * <p>
     * {@snippet :
     *     S3AsyncClient s3 = S3AsyncClient.create(); // Use one client for your whole application!
     *
     *     byte[] dataToSend = "Hello".getBytes(StandardCharsets.UTF_8);
     *     InputStream streamToSend = new ByteArrayInputStream();
     *     long streamToSendLength = dataToSend.length();
     *
     *     // Start the operation
     *     BlockingInputStreamAsyncRequestBody body =
     *         AsyncRequestBody.forBlockingInputStream(streamToSendLength);
     *     CompletableFuture<PutObjectResponse> responseFuture =
     *         s3.putObject(r -> r.bucket("bucketName").key("key"), body);
     *
     *     // Write the input stream to the running operation
     *     body.writeInputStream(streamToSend);
     *
     *     // Wait for the service to respond.
     *     PutObjectResponse response = responseFuture.join();
     * }
     */
    static BlockingInputStreamAsyncRequestBody forBlockingInputStream(Long contentLength) {
        return new BlockingInputStreamAsyncRequestBody(contentLength);
    }

    /**
     * Creates a {@link BlockingOutputStreamAsyncRequestBody} to use for writing to the downstream service as if it's an output
     * stream. Retries are not supported for this request body.
     *
     * <p>The caller is responsible for calling {@link OutputStream#close()} on the
     * {@link BlockingOutputStreamAsyncRequestBody#outputStream()} when writing is complete.
     *
     * <p><b>Example Usage</b>
     * <p>
     * {@snippet :
     *     S3AsyncClient s3 = S3AsyncClient.create(); // Use one client for your whole application!
     *
     *     byte[] dataToSend = "Hello".getBytes(StandardCharsets.UTF_8);
     *     long lengthOfDataToSend = dataToSend.length();
     *
     *     // Start the operation
     *     BlockingInputStreamAsyncRequestBody body =
     *         AsyncRequestBody.forBlockingOutputStream(lengthOfDataToSend);
     *     CompletableFuture<PutObjectResponse> responseFuture =
     *         s3.putObject(r -> r.bucket("bucketName").key("key"), body);
     *
     *     // Write the input stream to the running operation
     *     try (CancellableOutputStream outputStream = body.outputStream()) {
     *         outputStream.write(dataToSend);
     *     }
     *
     *     // Wait for the service to respond.
     *     PutObjectResponse response = responseFuture.join();
     * }
     */
    static BlockingOutputStreamAsyncRequestBody forBlockingOutputStream(Long contentLength) {
        return new BlockingOutputStreamAsyncRequestBody(contentLength);
    }

    /**
     * Creates an {@link AsyncRequestBody} with no content.
     *
     * @return AsyncRequestBody instance.
     */
    static AsyncRequestBody empty() {
        return fromBytes(new byte[0]);
    }


    /**
     * Converts this {@link AsyncRequestBody} to a publisher of {@link AsyncRequestBody}s, each of which publishes a specific
     * portion of the original data, based on the configured {code chunkSizeInBytes}.
     *
     * <p>
     * If content length of this {@link AsyncRequestBody} is present, each divided {@link AsyncRequestBody} is delivered to the
     * subscriber right after it's initialized.
     * <p>
     * // TODO: API Surface Area review: should we make this behavior configurable?
     * If content length is null, it is sent after the entire content for that chunk is buffered.
     * In this case, the configured {@code maxMemoryUsageInBytes} must be larger than or equal to {@code chunkSizeInBytes}.
     *
     * @param chunkSizeInBytes      the size for each divided chunk. The last chunk may be smaller than the configured size.
     * @param maxMemoryUsageInBytes the max memory the SDK will use to buffer the content
     * @return SplitAsyncRequestBodyResult
     */
    default SplitAsyncRequestBodyResponse split(long chunkSizeInBytes, long maxMemoryUsageInBytes) {
        Validate.isPositive(chunkSizeInBytes, "chunkSizeInBytes");
        Validate.isPositive(maxMemoryUsageInBytes, "maxMemoryUsageInBytes");

        if (!this.contentLength().isPresent()) {
            Validate.isTrue(maxMemoryUsageInBytes >= chunkSizeInBytes,
                            "maxMemoryUsageInBytes must be larger than or equal to " +
                            "chunkSizeInBytes if the content length is unknown");
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        SplittingPublisher splittingPublisher = SplittingPublisher.builder()
                                                                  .asyncRequestBody(this)
                                                                  .chunkSizeInBytes(chunkSizeInBytes)
                                                                  .maxMemoryUsageInBytes(maxMemoryUsageInBytes)
                                                                  .resultFuture(future)
                                                                  .build();

        return SplitAsyncRequestBodyResponse.create(splittingPublisher, future);
    }
}
