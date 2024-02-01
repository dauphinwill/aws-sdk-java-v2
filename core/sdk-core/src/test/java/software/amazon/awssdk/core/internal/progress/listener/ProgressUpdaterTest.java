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

package software.amazon.awssdk.core.internal.progress.listener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkRequestOverrideConfiguration;
import software.amazon.awssdk.core.http.NoopTestRequest;
import software.amazon.awssdk.core.progress.listener.ProgressListener;

public class ProgressUpdaterTest {
    private CaptureProgressListener captureProgressListener;

    private static final long BYTES_TRANSFERRED = 5L;

    @BeforeEach
    void initiate() {
        captureProgressListener = new CaptureProgressListener();
    }

        private static Stream<Arguments> contentLength() {
        return Stream.of(
            Arguments.of(100L),
            Arguments.of(200L),
            Arguments.of(300L),
            Arguments.of(400L),
            Arguments.of(500L));
    }

    @Test
    public void requestPrepared_transferredBytes_equals_zero() {

        CaptureProgressListener mockListener = Mockito.mock(CaptureProgressListener.class);

        SdkRequestOverrideConfiguration.Builder builder = SdkRequestOverrideConfiguration.builder();
        builder.progressListeners(Arrays.asList(mockListener, captureProgressListener));

        SdkRequestOverrideConfiguration overrideConfig = builder.build();

        SdkRequest sdkRequest = NoopTestRequest.builder()
                                               .overrideConfiguration(overrideConfig)
                                               .build();

        ProgressUpdater progressUpdater = new ProgressUpdater(sdkRequest, null);
        progressUpdater.requestPrepared();

        assertEquals(0.0, progressUpdater.requestBodyProgress().progressSnapshot().transferredBytes(), 0.0);
        assertTrue(captureProgressListener.requestPrepared());
        assertFalse(captureProgressListener.requestHeaderSent());
        assertFalse(captureProgressListener.responseHeaderReceived());
        Mockito.verify(mockListener, never()).executionFailure(ArgumentMatchers.any(ProgressListener.Context.ExecutionFailure.class));
        Mockito.verify(mockListener, never()).attemptFailure(ArgumentMatchers.any(ProgressListener.Context.AttemptFailure.class));
        Mockito.verify(mockListener, never()).attemptFailureResponseBytesReceived(ArgumentMatchers.any(ProgressListener.Context.AttemptFailureResponseBytesReceived.class));
        Mockito.verify(mockListener, times(1)).requestPrepared(ArgumentMatchers.any(ProgressListener.Context.RequestPrepared.class));


    }

    @Test
    public void requestHeaderSent_transferredBytes_equals_zero() {

        CaptureProgressListener mockListener = Mockito.mock(CaptureProgressListener.class);

        SdkRequestOverrideConfiguration.Builder builder = SdkRequestOverrideConfiguration.builder();
        builder.progressListeners(Arrays.asList(mockListener, captureProgressListener));

        SdkRequestOverrideConfiguration overrideConfig = builder.build();

        SdkRequest sdkRequest = NoopTestRequest.builder()
                                               .overrideConfiguration(overrideConfig)
                                               .build();

        ProgressUpdater progressUpdater = new ProgressUpdater(sdkRequest, null);
        progressUpdater.requestHeaderSent();

        assertEquals(0.0, progressUpdater.requestBodyProgress().progressSnapshot().transferredBytes(), 0.0);
        assertFalse(captureProgressListener.requestPrepared());
        assertTrue(captureProgressListener.requestHeaderSent());
        assertFalse(captureProgressListener.responseHeaderReceived());
        Mockito.verify(mockListener, never()).executionFailure(ArgumentMatchers.any(ProgressListener.Context.ExecutionFailure.class));
        Mockito.verify(mockListener, never()).attemptFailure(ArgumentMatchers.any(ProgressListener.Context.AttemptFailure.class));
        Mockito.verify(mockListener, never()).attemptFailureResponseBytesReceived(ArgumentMatchers.any(ProgressListener.Context.AttemptFailureResponseBytesReceived.class));
        Mockito.verify(mockListener, times(1)).requestHeaderSent(ArgumentMatchers.any(ProgressListener.Context.RequestHeaderSent.class));

    }

    @Test
    public void requestBytesSent_transferredBytes() {

        CaptureProgressListener mockListener = Mockito.mock(CaptureProgressListener.class);

        SdkRequestOverrideConfiguration.Builder builder = SdkRequestOverrideConfiguration.builder();
        builder.progressListeners(Arrays.asList(mockListener, captureProgressListener));

        SdkRequestOverrideConfiguration overrideConfig = builder.build();

        SdkRequest sdkRequest = NoopTestRequest.builder()
                                               .overrideConfiguration(overrideConfig)
                                               .build();

        ProgressUpdater progressUpdater = new ProgressUpdater(sdkRequest, null);
        progressUpdater.incrementBytesSent(BYTES_TRANSFERRED);
        assertEquals(BYTES_TRANSFERRED, progressUpdater.requestBodyProgress().progressSnapshot().transferredBytes(), 0.0);

        progressUpdater.incrementBytesSent(BYTES_TRANSFERRED);
        assertEquals(BYTES_TRANSFERRED + BYTES_TRANSFERRED, progressUpdater.requestBodyProgress().progressSnapshot().transferredBytes(), 0.0);

        Mockito.verify(mockListener, never()).executionFailure(ArgumentMatchers.any(ProgressListener.Context.ExecutionFailure.class));
        Mockito.verify(mockListener, never()).attemptFailure(ArgumentMatchers.any(ProgressListener.Context.AttemptFailure.class));
        Mockito.verify(mockListener, never()).attemptFailureResponseBytesReceived(ArgumentMatchers.any(ProgressListener.Context.AttemptFailureResponseBytesReceived.class));
        Mockito.verify(mockListener, times(2)).requestBytesSent(ArgumentMatchers.any(ProgressListener.Context.RequestBytesSent.class));

    }

    @ParameterizedTest
    @MethodSource("contentLength")
    public void ratioTransferred_upload_transferredBytes(long contentLength) {

        CaptureProgressListener mockListener = Mockito.mock(CaptureProgressListener.class);

        SdkRequestOverrideConfiguration.Builder builder = SdkRequestOverrideConfiguration.builder();
        builder.progressListeners(Arrays.asList(mockListener, captureProgressListener));

        SdkRequestOverrideConfiguration overrideConfig = builder.build();

        SdkRequest sdkRequest = NoopTestRequest.builder()
                                               .overrideConfiguration(overrideConfig)
                                               .build();

        ProgressUpdater progressUpdater = new ProgressUpdater(sdkRequest, contentLength);
        progressUpdater.incrementBytesSent(BYTES_TRANSFERRED);
        assertEquals((double) BYTES_TRANSFERRED / contentLength, progressUpdater.requestBodyProgress().progressSnapshot().ratioTransferred().getAsDouble(), 0.0);

    }

    @Test
    public void responseHeaderReceived_transferredBytes_equals_zero() {

        CaptureProgressListener mockListener = Mockito.mock(CaptureProgressListener.class);

        SdkRequestOverrideConfiguration.Builder builder = SdkRequestOverrideConfiguration.builder();
        builder.progressListeners(Arrays.asList(mockListener, captureProgressListener));

        SdkRequestOverrideConfiguration overrideConfig = builder.build();

        SdkRequest sdkRequest = NoopTestRequest.builder()
                                               .overrideConfiguration(overrideConfig)
                                               .build();

        ProgressUpdater progressUpdater = new ProgressUpdater(sdkRequest, null);
        progressUpdater.responseHeaderReceived();

        assertEquals(0.0, progressUpdater.requestBodyProgress().progressSnapshot().transferredBytes(), 0.0);
        assertFalse(captureProgressListener.requestPrepared());
        assertFalse(captureProgressListener.requestHeaderSent());
        assertTrue(captureProgressListener.responseHeaderReceived());
        Mockito.verify(mockListener, never()).executionFailure(ArgumentMatchers.any(ProgressListener.Context.ExecutionFailure.class));
        Mockito.verify(mockListener, never()).attemptFailure(ArgumentMatchers.any(ProgressListener.Context.AttemptFailure.class));
        Mockito.verify(mockListener, never()).attemptFailureResponseBytesReceived(ArgumentMatchers.any(ProgressListener.Context.AttemptFailureResponseBytesReceived.class));
        Mockito.verify(mockListener, times(1)).responseHeaderReceived(ArgumentMatchers.any(ProgressListener.Context.ResponseHeaderReceived.class));

    }

    @Test
    public void responseBytesReceived_transferredBytes_valid() {

        CaptureProgressListener mockListener = Mockito.mock(CaptureProgressListener.class);

        SdkRequestOverrideConfiguration.Builder builder = SdkRequestOverrideConfiguration.builder();
        builder.progressListeners(Arrays.asList(mockListener, captureProgressListener));

        SdkRequestOverrideConfiguration overrideConfig = builder.build();

        SdkRequest sdkRequest = NoopTestRequest.builder()
                                               .overrideConfiguration(overrideConfig)
                                               .build();

        ProgressUpdater progressUpdater = new ProgressUpdater(sdkRequest, null);
        progressUpdater.incrementBytesReceived(BYTES_TRANSFERRED);
        assertEquals(BYTES_TRANSFERRED, progressUpdater.responseBodyProgress().progressSnapshot().transferredBytes(), 0.0);

        progressUpdater.incrementBytesReceived(BYTES_TRANSFERRED);
        assertEquals(BYTES_TRANSFERRED + BYTES_TRANSFERRED, progressUpdater.responseBodyProgress().progressSnapshot().transferredBytes(), 0.0);

        Mockito.verify(mockListener, never()).executionFailure(ArgumentMatchers.any(ProgressListener.Context.ExecutionFailure.class));
        Mockito.verify(mockListener, never()).attemptFailure(ArgumentMatchers.any(ProgressListener.Context.AttemptFailure.class));
        Mockito.verify(mockListener, never()).attemptFailureResponseBytesReceived(ArgumentMatchers.any(ProgressListener.Context.AttemptFailureResponseBytesReceived.class));
        Mockito.verify(mockListener, times(2)).responseBytesReceived(ArgumentMatchers.any(ProgressListener.Context.ResponseBytesReceived.class));

    }
}
