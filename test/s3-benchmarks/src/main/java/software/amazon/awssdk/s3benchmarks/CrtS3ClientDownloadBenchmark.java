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

package software.amazon.awssdk.s3benchmarks;

import static software.amazon.awssdk.s3benchmarks.BenchmarkUtils.printOutResult;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import software.amazon.awssdk.crt.http.HttpHeader;
import software.amazon.awssdk.crt.http.HttpRequest;
import software.amazon.awssdk.crt.s3.S3MetaRequest;
import software.amazon.awssdk.crt.s3.S3MetaRequestOptions;
import software.amazon.awssdk.crt.s3.S3MetaRequestResponseHandler;
import software.amazon.awssdk.utils.Validate;

public class CrtS3ClientDownloadBenchmark extends BaseCrtClientBenchmark {

    private final long contentLength;
    private final software.amazon.awssdk.services.s3.S3Client s3Sync;

    public CrtS3ClientDownloadBenchmark(TransferManagerBenchmarkConfig config) {
        super(config);
        Validate.isNull(config.filePath(), "File path is not supported in CrtS3ClientDownloadBenchmark");
        this.s3Sync = software.amazon.awssdk.services.s3.S3Client.builder().build();
        this.contentLength = s3Sync.headObject(b -> b.bucket(bucket).key(key)).contentLength();
    }

    @Override
    protected void sendOneRequest(List<Double> latencies) {

        CompletableFuture<Void> resultFuture = new CompletableFuture<>();

        S3MetaRequestResponseHandler responseHandler = new TestS3MetaRequestResponseHandler(resultFuture);

        String endpoint = bucket + ".s3." + region + ".amazonaws.com";

        HttpHeader[] headers = {new HttpHeader("Host", endpoint)};
        HttpRequest httpRequest = new HttpRequest("GET", "/" + key, headers, null);

        S3MetaRequestOptions metaRequestOptions = new S3MetaRequestOptions()
            .withEndpoint(URI.create("https://" + endpoint))
            .withMetaRequestType(S3MetaRequestOptions.MetaRequestType.GET_OBJECT).withHttpRequest(httpRequest)
            .withResponseHandler(responseHandler);

        long start = System.currentTimeMillis();
        try (S3MetaRequest metaRequest = crtS3Client.makeMetaRequest(metaRequestOptions)) {
            resultFuture.get(10, TimeUnit.MINUTES);
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        long end = System.currentTimeMillis();
        latencies.add((end - start) / 1000.0);
    }

    @Override
    protected void onResult(List<Double> metrics) throws IOException {
        printOutResult(metrics, "Download to File", contentLength);
    }

    @Override
    protected void cleanup() {
        s3Sync.close();
        super.cleanup();
    }
}