/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.client;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.test.rest.ESRestTestCase;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * This is temporarily duplicated from the server side.
 * @TODO Replace with an implementation using the HLRC once
 * the APIs for managing datafeeds are implemented.
 */
public class MlRestTestStateCleaner {

    private final Logger logger;
    private final RestClient adminClient;

    public MlRestTestStateCleaner(Logger logger, RestClient adminClient) {
        this.logger = logger;
        this.adminClient = adminClient;
    }

    public void clearMlMetadata() throws IOException {
        deleteAllDatafeeds();
        deleteAllJobs();
        // indices will be deleted by the ESRestTestCase class
    }

    @SuppressWarnings("unchecked")
    private void deleteAllDatafeeds() throws IOException {
        final Request datafeedsRequest = new Request("GET", "/_xpack/ml/datafeeds");
        datafeedsRequest.addParameter("filter_path", "datafeeds");
        final Response datafeedsResponse = adminClient.performRequest(datafeedsRequest);
        final List<Map<String, Object>> datafeeds =
                (List<Map<String, Object>>) XContentMapValues.extractValue("datafeeds", ESRestTestCase.entityAsMap(datafeedsResponse));
        if (datafeeds == null) {
            return;
        }

        try {
            adminClient.performRequest(new Request("POST", "/_xpack/ml/datafeeds/_all/_stop"));
        } catch (Exception e1) {
            logger.warn("failed to stop all datafeeds. Forcing stop", e1);
            try {
                adminClient.performRequest(new Request("POST", "/_xpack/ml/datafeeds/_all/_stop?force=true"));
            } catch (Exception e2) {
                logger.warn("Force-closing all data feeds failed", e2);
            }
            throw new RuntimeException(
                    "Had to resort to force-stopping datafeeds, something went wrong?", e1);
        }

        for (Map<String, Object> datafeed : datafeeds) {
            String datafeedId = (String) datafeed.get("datafeed_id");
            adminClient.performRequest(new Request("DELETE", "/_xpack/ml/datafeeds/" + datafeedId));
        }
    }

    private void deleteAllJobs() throws IOException {
        final Request jobsRequest = new Request("GET", "/_xpack/ml/anomaly_detectors");
        jobsRequest.addParameter("filter_path", "jobs");
        final Response response = adminClient.performRequest(jobsRequest);
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> jobConfigs =
                (List<Map<String, Object>>) XContentMapValues.extractValue("jobs", ESRestTestCase.entityAsMap(response));
        if (jobConfigs == null) {
            return;
        }

        try {
            adminClient.performRequest(new Request("POST", "/_xpack/ml/anomaly_detectors/_all/_close"));
        } catch (Exception e1) {
            logger.warn("failed to close all jobs. Forcing closed", e1);
            try {
                adminClient.performRequest(new Request("POST", "/_xpack/ml/anomaly_detectors/_all/_close?force=true"));
            } catch (Exception e2) {
                logger.warn("Force-closing all jobs failed", e2);
            }
            throw new RuntimeException("Had to resort to force-closing jobs, something went wrong?",
                    e1);
        }

        for (Map<String, Object> jobConfig : jobConfigs) {
            String jobId = (String) jobConfig.get("job_id");
            adminClient.performRequest(new Request("DELETE", "/_xpack/ml/anomaly_detectors/" + jobId));
        }
    }
}
