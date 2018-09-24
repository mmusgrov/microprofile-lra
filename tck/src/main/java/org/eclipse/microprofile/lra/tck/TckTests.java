/*
 *******************************************************************************
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.eclipse.microprofile.lra.tck;

import org.eclipse.microprofile.lra.annotation.CompensatorStatus;
import org.eclipse.microprofile.lra.client.GenericLRAException;
import org.eclipse.microprofile.lra.client.InvalidLRAException;
import org.eclipse.microprofile.lra.client.http.LRAHttpClient;
import org.eclipse.microprofile.lra.client.LRAId;
import org.eclipse.microprofile.lra.client.LRAInfo;
import org.eclipse.microprofile.lra.client.http.LRAUrl;
import org.eclipse.microprofile.lra.tck.participant.api.ActivityController;
import org.eclipse.microprofile.lra.tck.participant.api.TimedParticipant;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import static org.eclipse.microprofile.lra.client.http.LRAHttpClient.LRA_COORDINATOR_HOST_KEY;
import static org.eclipse.microprofile.lra.client.http.LRAHttpClient.LRA_COORDINATOR_PORT_KEY;
import static org.eclipse.microprofile.lra.client.http.LRAHttpClient.LRA_COORDINATOR_PATH_KEY;
import static org.eclipse.microprofile.lra.client.http.LRAHttpClient.LRA_RECOVERY_PATH_KEY;
import static org.eclipse.microprofile.lra.tck.participant.api.ActivityController.ACTIVITIES_PATH;
import static org.eclipse.microprofile.lra.tck.participant.api.StandardController.ACTIVITIES_PATH3;
import static org.eclipse.microprofile.lra.tck.participant.api.StandardController.NON_TRANSACTIONAL_WORK;
import static org.eclipse.microprofile.lra.tck.participant.api.TimedParticipant.ACTIVITIES_PATH2;

public class TckTests {
    private static final Long LRA_TIMEOUT_MILLIS = 50000L;
    private static URL micrserviceBaseUrl;
    private static URL rcBaseUrl;

    private static final int COORDINATOR_SWARM_PORT = 8082;
    private static final int TEST_SWARM_PORT = 8080;

    private static final String PASSED_TEXT = "passed";
    private static final String FAILED_TEXT = "failed";
    private static final String WORK_PATH = "work";
    private static final String STATUS_TEXT = "status";

    private static LRAHttpClient lraClient; // TODO test suite should run agains LRAClient
    private static Client msClient;
    private static Client rcClient;
    private static List<LRAInfo> oldLRAs;

    private WebTarget msTarget;
    private WebTarget recoveryTarget;

    private Response response;

    private enum CompletionType {
        complete, compensate, mixed
    }

    @BeforeClass
    public static void beforeClass(LRAHttpClient lraClient) {
        initTck(lraClient);
    }

    public TckResult runTck(LRAHttpClient lraClient, String testname, boolean verbose) {
        TckResult run = new TckResult();

        initTck(lraClient);

        run.add("startLRA", TckTests::startLRA, verbose);
        run.add("cancelLRA", TckTests::cancelLRA, verbose);
        run.add("closeLRA", TckTests::closeLRA, verbose);
        run.add("getActiveLRAs", TckTests::getActiveLRAs, verbose);
        run.add("getAllLRAs", TckTests::getAllLRAs, verbose);
        run.add("isActiveLRA", TckTests::isActiveLRA, verbose);
        run.add("nestedActivity", TckTests::nestedActivity, verbose);
        run.add("completeMultiLevelNestedActivity", TckTests::completeMultiLevelNestedActivity, verbose);
        run.add("compensateMultiLevelNestedActivity", TckTests::compensateMultiLevelNestedActivity, verbose);
        run.add("mixedMultiLevelNestedActivity", TckTests::mixedMultiLevelNestedActivity, verbose);
        run.add("joinLRAViaHeader", TckTests::joinLRAViaHeader, verbose);
        run.add("join", TckTests::join, verbose);
        run.add("leaveLRA", TckTests::leaveLRA, verbose);
        run.add("leaveLRAViaAPI", TckTests::leaveLRAViaAPI, verbose);
        run.add("dependentLRA", TckTests::dependentLRA, verbose);
//        run.add("cancelOn", TckTests::cancelOn, verbose);
//        run.add("cancelOnFamily", TckTests::cancelOnFamily, verbose);
        run.add("acceptTest", TckTests::acceptTest, verbose);

        run.add("noLRATest", TckTests::noLRATest, verbose);
        run.add("closeLRAWaitForRecovery", TckTests::closeLRAWaitForRecovery, verbose);
        run.add("closeLRAWaitIndefinitely", TckTests::closeLRAWaitIndefinitely, verbose);
        run.add("connectionHangup", TckTests::connectionHangup, verbose);
        run.add("joinLRAViaBody", TckTests::joinLRAViaBody, verbose);
        run.add("timeLimitRequiredLRA", TckTests::timeLimitRequiredLRA, verbose);

        run.add("participantTimeLimitSupportsLRA", TckTests::participantTimeLimitSupportsLRA, verbose);

        run.runTests(this, testname);

        return run;
    }

    private static void initTck(LRAHttpClient lraClient) {
        TckTests.lraClient = lraClient;

        try {
            if (Boolean.valueOf(System.getProperty("enablePause", "true"))) {
                System.out.println("Getting ready to connect - expecting swarm lra coordinator is already up...");
                Thread.sleep(1000);
            }

            int servicePort = Integer.getInteger("service.http.port", TEST_SWARM_PORT);
            String rcHost = System.getProperty(LRA_COORDINATOR_HOST_KEY, "localhost");
            int rcPort = Integer.getInteger(LRA_COORDINATOR_PORT_KEY, COORDINATOR_SWARM_PORT);
            String coordinatorPath = System.getProperty(LRA_COORDINATOR_PATH_KEY, "lra-coordinator");

            micrserviceBaseUrl = new URL(String.format("http://localhost:%d", servicePort));
            rcBaseUrl = new URL(String.format("http://%s:%d", rcHost, rcPort));

            lraClient.setCoordinatorURI(new URI(String.format("http://%s:%d/%s", rcHost, rcPort, coordinatorPath)));
            msClient = ClientBuilder.newClient();
            rcClient = ClientBuilder.newClient();

            oldLRAs = new ArrayList<>();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @AfterClass
    public static void afterClass() {
        oldLRAs.clear();
        lraClient.close();
        msClient.close();
        rcClient.close();
    }

    @Before
    public void before() {
        try {
            response = null;
            msTarget = msClient.target(URI.create(new URL(micrserviceBaseUrl, "/").toExternalForm()));
            recoveryTarget = rcClient.target(URI.create(new URL(rcBaseUrl, "/").toExternalForm()));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @After
    public void after() {
        List<LRAInfo> activeLRAs = lraClient.getActiveLRAs();

        if (response != null) {
            response.close();
        }

        if (activeLRAs.size() != 0) {
            activeLRAs.forEach(lra -> {
                try {
                    if (!oldLRAs.contains(lra)) {
                        System.out.printf("%s: WARNING: test did not close %s%n", "testName.getMethodName()", lra.getLraId());
                        oldLRAs.add(lra);
                        lraClient.closeLRA(new LRAId(lra.getLraId()));
                    }
                } catch (WebApplicationException e) {
                    System.out.printf("After Test: exception %s closing %s%n", e.getMessage(), lra.getLraId());
                }
            });
        }

        WebTarget resourcePath = msTarget.path(ACTIVITIES_PATH).path(ActivityController.END_TEST_RESOURCE_METHOD)
                .queryParam("how", "cleanup");

        try {
            if (lraClient.getCurrent() != null) {
                lraClient.closeLRA(lraClient.getCurrent());
            }

            response = resourcePath.request().put(Entity.text(""));
        } catch (Exception ignore) {
        } finally {
            if (response != null) {
                response.close();
            }

            response = null;
        }
    }

    @Test
    private String startLRA() throws WebApplicationException {
        LRAId lra = lraClient.startLRA(null, "SpecTest#startLRA", LRA_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        lraClient.closeLRA(lra);

        return lra.toString();
    }

    @Test
    private String cancelLRA() throws WebApplicationException {
        LRAId lra = lraClient.startLRA(null,"SpecTest#cancelLRA", LRA_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        lraClient.cancelLRA(lra);

        List<LRAInfo> lras = lraClient.getAllLRAs();

        assertTrue(!getLra(lras, lra.toString()).isPresent(),
                "cancelLRA via client: lra still active", null, lra);

        return lra.toString();
    }

    @Test
    private String closeLRA() throws WebApplicationException {
        LRAId lra = lraClient.startLRA(null, "SpecTest#closelLRA", LRA_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        lraClient.closeLRA(lra);

        List<LRAInfo> lras = lraClient.getAllLRAs();

        assertTrue(!getLra(lras, lra.toString()).isPresent(), "closeLRA via client: lra still active",
                null, lra);

        return lra.toString();
    }

    // the coordinator cleans up when canceled
    @Test
    private String isCompensatedLRA() throws WebApplicationException {
        LRAId lra = lraClient.startLRA(null, "SpecTest#isCompensatedLRA", LRA_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        lraClient.cancelLRA(lra);

        assertTrue(lraClient.isCompensatedLRA(lra), null, null, lra);

        return lra.toString();
    }

    // the coordinator cleans up when completed
    @Test
    private String isCompletedLRA() throws WebApplicationException {
        LRAId lra = lraClient.startLRA(null, "SpecTest#isCompletedLRA", LRA_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        lraClient.closeLRA(lra);

        assertTrue(lraClient.isCompletedLRA(lra), null, null, lra);

        return lra.toString();
    }

    @Test
    private String joinLRAViaBody() throws WebApplicationException {

        WebTarget resourcePath = msTarget.path(ACTIVITIES_PATH).path(WORK_PATH);
        response = resourcePath.request().put(Entity.text(""));

        String lra = checkStatusAndClose(response, Response.Status.OK.getStatusCode(), true, resourcePath);

        // validate that the LRA coordinator no longer knows about lraId
        List<LRAInfo> lras = lraClient.getActiveLRAs();

        // the resource /activities/work is annotated with Type.REQUIRED so the container should have ended it
        assertTrue(!getLra(lras, lra).isPresent(), "joinLRAViaBody: lra is still active",
                resourcePath, null);

        return PASSED_TEXT;
    }

    @Test
    private String nestedActivity() throws WebApplicationException {
        LRAId lra = lraClient.startLRA(null, "SpecTest#nestedActivity", LRA_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        WebTarget resourcePath = msTarget
                .path(ACTIVITIES_PATH).path("nestedActivity");

        response = resourcePath
                .request()
                .header(LRAHttpClient.LRA_HTTP_HEADER, lra)
                .put(Entity.text(""));

        Object parentId = response.getHeaders().getFirst(LRAHttpClient.LRA_HTTP_HEADER);

        assertNotNull(parentId, "nestedActivity: null parent LRA", resourcePath);
        assertEquals(lra.toString(), parentId.toString(), "nestedActivity should have returned the parent LRA", resourcePath);

        String nestedLraId = checkStatusAndClose(response, Response.Status.OK.getStatusCode(), true, resourcePath);

        response = null;

        // close the LRA
        lraClient.closeLRA(lra);

        // validate that the nested LRA was closed
        List<LRAInfo> lras = lraClient.getActiveLRAs();

        // the resource /activities/work is annotated with Type.REQUIRED so the container should have ended it
        assertTrue(!getLra(lras, nestedLraId).isPresent(), "nestedActivity: nested LRA should not be active",
                resourcePath, lra);

        return lra.toString();
    }

    @Test
    private String completeMultiLevelNestedActivity() throws WebApplicationException {
        return multiLevelNestedActivity(CompletionType.complete, 1);
    }

    @Test
    private String compensateMultiLevelNestedActivity() throws WebApplicationException {
        return multiLevelNestedActivity(CompletionType.compensate, 1);
    }

    @Test
    private String mixedMultiLevelNestedActivity() throws WebApplicationException {
        return multiLevelNestedActivity(CompletionType.mixed, 2);
    }

    @Test
    private String joinLRAViaHeader() throws WebApplicationException {
        int cnt1 = completedCount(ACTIVITIES_PATH, true);

        LRAId lra = lraClient.startLRA(null, "SpecTest#joinLRAViaBody", LRA_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        WebTarget resourcePath = msTarget.path(ACTIVITIES_PATH).path(WORK_PATH);
        response = resourcePath
                .request().header(LRAHttpClient.LRA_HTTP_HEADER, lra).put(Entity.text(""));
        checkStatusAndClose(response, Response.Status.OK.getStatusCode(), false, resourcePath);
        response = null;

        // validate that the LRA coordinator still knows about lraId
        List<LRAInfo> lras = lraClient.getActiveLRAs();
        assertTrue(getLra(lras, lra.toString()).isPresent(), "joinLRAViaHeader: missing lra",
                resourcePath, lra);

        // close the LRA
        lraClient.closeLRA(lra);

        // check that LRA coordinator no longer knows about lraId
        lras = lraClient.getActiveLRAs();
        assertTrue(!getLra(lras, lra.toString()).isPresent(), "joinLRAViaHeader: LRA should not be active",
                resourcePath, lra);

        // check that participant was told to complete
        int cnt2 = completedCount(ACTIVITIES_PATH, true);
        assertEquals(cnt1 + 1, cnt2, "joinLRAViaHeader: wrong completion count", resourcePath);

        return PASSED_TEXT;
    }

    @Test
    private String join() throws WebApplicationException {
        List<LRAInfo> lras = lraClient.getActiveLRAs();
        int count = lras.size();
        LRAId lra = lraClient.startLRA(null, "SpecTest#join", LRA_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        WebTarget resourcePath = msTarget.path(ACTIVITIES_PATH).path(WORK_PATH);
        response = resourcePath
                .request().header(LRAHttpClient.LRA_HTTP_HEADER, lra).put(Entity.text(""));
        checkStatusAndClose(response, Response.Status.OK.getStatusCode(), false, resourcePath);
        response = null;
        lraClient.closeLRA(lra);

        lras = lraClient.getActiveLRAs();
        System.out.printf("join ok %d versus %d lras%n", count, lras.size());
        assertEquals(count, lras.size(), "join: wrong LRA count", resourcePath);

        return lra.toString();
    }

    @Test
    private String leaveLRA() throws WebApplicationException {
        int cnt1 = completedCount(ACTIVITIES_PATH, true);
        LRAId lra = lraClient.startLRA(null, "SpecTest#leaveLRA", LRA_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        WebTarget resourcePath = msTarget.path(ACTIVITIES_PATH).path(WORK_PATH);
        response = resourcePath.request().header(LRAHttpClient.LRA_HTTP_HEADER, lra).put(Entity.text(""));

        checkStatusAndClose(response, Response.Status.OK.getStatusCode(), false, resourcePath);
        response = null;

        // perform a second request to the same method in the same LRA context to validate that multiple participants are not registered
        resourcePath = msTarget.path(ACTIVITIES_PATH).path(WORK_PATH);
        response = resourcePath.request().header(LRAHttpClient.LRA_HTTP_HEADER, lra).put(Entity.text(""));
        checkStatusAndClose(response, Response.Status.OK.getStatusCode(), false, resourcePath);
        response = null;

        // call a method annotated with @Leave (should remove the participant from the LRA)
        resourcePath = msTarget.path(ACTIVITIES_PATH).path("leave");
        response = resourcePath.request().header(LRAHttpClient.LRA_HTTP_HEADER, lra).put(Entity.text(""));
        checkStatusAndClose(response, Response.Status.OK.getStatusCode(), false, resourcePath);
        response = null;

        // lraClient.leaveLRA(lra, "some participant"); // ask the MS for the participant url so we can test LRAOldClient

        lraClient.closeLRA(lra);

        // check that participant was not told to complete
        int cnt2 = completedCount(ACTIVITIES_PATH, true);

        assertEquals(cnt1, cnt2, "leaveLRA: wrong completion count", resourcePath);

        return lra.toString();
    }

    @Test
    private String leaveLRAViaAPI() throws WebApplicationException {
        int cnt1 = completedCount(ACTIVITIES_PATH, true);
        LRAId lra = lraClient.startLRA(null, "SpecTest#leaveLRA", LRA_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        WebTarget resourcePath = msTarget.path(ACTIVITIES_PATH).path(WORK_PATH);

        response = resourcePath.request().header(LRAHttpClient.LRA_HTTP_HEADER, lra).put(Entity.text(""));
        checkStatusAndClose(response, Response.Status.OK.getStatusCode(), false, resourcePath);
        response = null;

        // perform a second request to the same method in the same LRA context to validate that multiple participants are not registered
        resourcePath = msTarget.path(ACTIVITIES_PATH).path(WORK_PATH);
        response = resourcePath.request().header(LRAHttpClient.LRA_HTTP_HEADER, lra).put(Entity.text(""));
        checkStatusAndClose(response, Response.Status.OK.getStatusCode(), false, resourcePath);
        response = null;

        // call a method annotated with @Leave (should remove the participant from the LRA)
        try {
            resourcePath = msTarget.path(ACTIVITIES_PATH).path("leave");
            response = resourcePath.path(URLEncoder.encode(lra.toString(), "UTF-8"))
                    .request().header(LRAHttpClient.LRA_HTTP_HEADER, lra).put(Entity.text(""));
        } catch (UnsupportedEncodingException e) {
            throw new WebApplicationException(
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(
                            Entity.text(String.format("%s: %s", resourcePath.getUri().toString(), e.getMessage()))).build());
        }
        checkStatusAndClose(response, Response.Status.OK.getStatusCode(), false, resourcePath);
        response = null;

        // lraClient.leaveLRA(lra, "some participant"); // ask the MS for the participant url so we can test LRAOldClient

        lraClient.closeLRA(lra);

        // check that participant was not told to complete
        int cnt2 = completedCount(ACTIVITIES_PATH, true);

        assertEquals(cnt1, cnt2,
                String.format("leaveLRAViaAPI: wrong count %d versus %d", cnt1, cnt2), resourcePath);

        return PASSED_TEXT;
    }

    @Test
    private String dependentLRA() throws WebApplicationException {
        // call a method annotated with NOT_SUPPORTED but one which programatically starts an LRA and returns it via a header
        WebTarget resourcePath = msTarget.path(ACTIVITIES_PATH).path("startViaApi");
        response = resourcePath.request().put(Entity.text(""));
        // check that the method started an LRA
        Object lraHeader = response.getHeaders().getFirst(LRAHttpClient.LRA_HTTP_HEADER);

        String id = checkStatusAndClose(response, Response.Status.OK.getStatusCode(), true, resourcePath);
        response = null;

        // the value returned via the header and body should be equal

        assertNotNull(lraHeader, String.format("JAX-RS response to PUT request should have returned the header %s",
                LRAHttpClient.LRA_HTTP_HEADER), resourcePath);
        assertNotNull(id, "JAX-RS response to PUT request should have returned content", resourcePath);
        assertEquals(id, lraHeader.toString(), "dependentLRA: resource returned wrong LRA", resourcePath);

        try {
            lraClient.closeLRA(new LRAUrl(new URL(lraHeader.toString())));
        } catch (MalformedURLException e) {
            throw new WebApplicationException(e);
        }

        return PASSED_TEXT;
    }

    @Test
    private String cancelOn() {
        cancelCheck("cancelOn");

        return PASSED_TEXT;
    }

    @Test
    private String cancelOnFamily() {
        cancelCheck("cancelOnFamily");

        return PASSED_TEXT;
    }

    /*
     * Participants can pass data during enlistment and this data will be returned during
     * the complete/compensate callbacks
     */
    private void testUserData() {
        List<LRAInfo> lras = lraClient.getActiveLRAs();
        int count = lras.size();
        String testData = "test participant data";
        WebTarget resourcePath = msTarget.path(ACTIVITIES_PATH).path("testUserData");

        response = resourcePath
                .request().put(Entity.text(testData));

        String activityId = response.readEntity(String.class);
        checkStatusAndClose(response, Response.Status.OK.getStatusCode(), false, resourcePath);
        response = null;

        lras = lraClient.getActiveLRAs();

        assertEquals(count, lras.size(), "testUserData: testUserData produced the wrong LRA count",
                resourcePath);

        response = msTarget.path(ACTIVITIES_PATH).path("getActivity")
                .queryParam("activityId", activityId)
                .request()
                .get();

        String activity = response.readEntity(String.class);

        // validate that the service received the correct data during the complete call
        assertTrue(activity.contains("userData='" + testData), null, null, null);
        assertTrue(activity.contains("endData='" + testData), null, null, null);
    }

    @Test
    // test participants that delay the complete/compensate callbacks by returning the HTTP 202 "Accepted" code
    // are completed by recovery
    private String acceptTest() throws WebApplicationException {
        joinAndEnd(true, true, ACTIVITIES_PATH, ActivityController.ACCEPT_WORK_RESOURCE_METHOD);

        return PASSED_TEXT;
    }

    // TODO the spec does not specifiy recovery semantics
    @Test
    private void joinAndEnd(boolean waitForRecovery, boolean close, String path, String path2) throws WebApplicationException {
        LRAId lra = lraClient.startLRA(null, "SpecTest#join", LRA_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        WebTarget resourcePath = msTarget.path(path).path(path2);
        long before = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli();
        response = resourcePath
                .request().header(LRAHttpClient.LRA_HTTP_HEADER, lra).put(Entity.text(""));

        checkStatusAndClose(response, Response.Status.OK.getStatusCode(), false, resourcePath);
        response = null;

        if (close) {
            lraClient.closeLRA(lra);
        } else {
            lraClient.cancelLRA(lra);
        }

        if (waitForRecovery) {
            waitForRecovery(3, lra);
        }

        // check that the lra was finished (either by recovery or directly)
        LRAInfo info = getLRA(lra);

        if (info != null) {
            long after = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli();

            String status = info.getStatus();
            long startTime = info.getStartTime();
            long finishTime = info.getFinishTime();
            long timeNow = info.getTimeNow();
            long age = timeNow - startTime;
            long ttl = finishTime - timeNow;

            System.out.printf("before: %d, after: %d diff: %d%n", before, after, after - before);
            System.out.printf("status: %s, timeNow: %d finishTime: %d age: %d ttl: %d%n",
                    status, timeNow, finishTime, age, ttl);

            throw new GenericLRAException(lra, 0,
                    "joinAndEnd via client: lra still active with status " + status, null);
        }
    }

    @Test
    private String participantTimeLimitSupportsLRA() {
        // start an LRA with a timeout longer than the participant timeout (see the TimedParticipant#compensateWork
        // the expectation is that when the participant timeout expires the LRA will be cancelled
        LRAId lra = lraClient.startLRA(null, "SpecTest#timeLimitSupportsLRA", LRA_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        long before = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli();
        int[] cnt1 = {completedCount(ACTIVITIES_PATH2, true),
                completedCount(ACTIVITIES_PATH2, false)};
        response = null;

        try {
            WebTarget resourcePath = msTarget.path(ACTIVITIES_PATH2).path(TimedParticipant.TIMELIMIT_SUPPRTS_RESOURCE_METHOD);
            response = resourcePath
                    .request()
                    .get();

            checkStatusAndClose(response, -1, true, resourcePath);
            response = null;

            try {
                // sleep for longer than specified in the @TimeLimit annotation on
                // the compensation method
                // {@link io.narayana.lra.participant.api.TimedParticipant#timeLimitSupportsLRA(String)}
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();
            }

            long after = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli();
            LRAInfo info = getLRA(lra);

            if (info != null) {
                String status = info.getStatus();
                long startTime = info.getStartTime();
                long finishTime = info.getFinishTime();
                long timeNow = info.getTimeNow();
                long age = timeNow - startTime;
                long ttl = finishTime - timeNow;

                if (CompensatorStatus.Compensating.name().equals(info.getStatus())) {
                    System.out.printf("INFO: participantTimeLimitSupportsLRA still compensating (%d %d %d)%n",
                            startTime, finishTime, timeNow);
                } else {
                    System.out.printf("before: %d, after: %d diff: %d%n", before, after, after - before);
                    System.out.printf("status: %s, timeNow: %d finishTime: %d age: %d ttl: %d%n",
                            status, timeNow, finishTime, age, ttl);

                    assertTrue(CompensatorStatus.Compensated.name().equals(status),
                            "LRA should have compensated: status=" + status, null, lra);
                }
            }

            int[] cnt2 = {completedCount(ACTIVITIES_PATH2, true),
                    completedCount(ACTIVITIES_PATH2, false)};

            // the timeout on the participant should have canceled the LRA so check that
            // the compensation ran
            assertEquals(cnt1[0], cnt2[0],"timeLimitSupportsLRA: complete was called instead of compensate", null);
            assertEquals(cnt1[1] + 1, cnt2[1], "timeLimitSupportsLRA: compensate should have been called", null);

            // now validate that he LRA was cancelled
            List<LRAInfo> lras = lraClient.getAllLRAs();

            assertTrue(!getLra(lras, lra.toString()).isPresent(),
                    "timeLimitSupportsLRA via client: lra still active", null, lra);

        } finally {

            if (response != null) {
                response.close();
            }

            response = null;
        }

        return PASSED_TEXT;
    }

    @Test
    public String timeLimitRequiredLRA() {
        int[] cnt1 = {completedCount(ACTIVITIES_PATH, true), completedCount(ACTIVITIES_PATH, false)};
        response = null;
        WebTarget resourcePath = msTarget.path(ACTIVITIES_PATH)
                .path(ActivityController.TIME_LIMIT_RESOURCE_METHOD);

        try {
            response = resourcePath
                    .request()
                    .get();

            checkStatusAndClose(response, -1, true, resourcePath);

            response = null;

            // check that participant was invoked
            int[] cnt2 = {completedCount(ACTIVITIES_PATH, true), completedCount(ACTIVITIES_PATH, false)};

            /*
             * The call to activities/timeLimitRequiredLRA should have started an LRA whch should have timed out
             *(because the called resource method sleeps for long than the @TimeLimit annotation specifies).
             * Therefore the it should have compensated:
             */
            assertEquals(cnt1[0], cnt2[0], "timeLimitRequiredLRA: complete was called instead of compensate",
                    resourcePath);
            assertEquals(cnt1[1] + 1, cnt2[1], "timeLimitRequiredLRA: compensate should have been called",
                    resourcePath);
        } finally {
            if (response != null) {
                response.close();
            }

            response = null;
        }

        return PASSED_TEXT;
    }

    @Test
    // test service A -> service B -> service C where A starts a LRA, service C starts a nested LRA and B is not LRA aware
    public String noLRATest() throws WebApplicationException {
        LRAId lra = lraClient.startLRA(null, "SpecTest#noLRATest", LRA_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        WebTarget resourcePath = msTarget.path(ACTIVITIES_PATH3).path(NON_TRANSACTIONAL_WORK);

        response = resourcePath.request().header(LRAHttpClient.LRA_HTTP_HEADER, lra).put(Entity.text(""));
        checkStatusAndClose(response, Response.Status.OK.getStatusCode(), true, resourcePath);
        response = null;

        lraClient.cancelLRA(lra);

        return PASSED_TEXT;
    }

    @Test
    public String closeLRAWaitForRecovery() throws WebApplicationException {
        return delayEndLRA(-1, "wait", "recovery");
    }

    @Test
    public String closeLRAWaitIndefinitely() throws WebApplicationException {
        return delayEndLRA(1000,"wait", "-1");
    }

    private String delayEndLRA(long maxMsecWait, String how, String arg) throws WebApplicationException {
        int[] cnt1 = {completedCount(ACTIVITIES_PATH, true), completedCount(ACTIVITIES_PATH, false)};

        LRAId lra = lraClient.startLRA(null, "SpecTest#delayEndLRA", LRA_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        WebTarget resourcePath = msTarget.path(ACTIVITIES_PATH).path(ActivityController.WORK_RESOURCE_METHOD)
                .queryParam("how", how)
                .queryParam("arg", arg);
        response = resourcePath
                .request().header(LRAHttpClient.LRA_HTTP_HEADER, lra).put(Entity.text(""));
        checkStatusAndClose(response, Response.Status.OK.getStatusCode(), false, resourcePath);
        response = null;

        tryEndLRA(false, lra, maxMsecWait, false);

        // check that participant was told to complete
        int[] cnt2 = {completedCount(ACTIVITIES_PATH, true), completedCount(ACTIVITIES_PATH, false)};

        assertEquals(cnt1[0] + 1, cnt2[0], "delayEndLRA: wrong completion count", resourcePath);
        assertEquals(cnt1[1], cnt2[1], "delayEndLRA: wrong compensation count", resourcePath);

        // the delay will cause responsibility for ending the LRA to pass to the recovery system so run a scan:
        waitForRecovery(1, lra);

        // check that the LRA has now been finished
        List<LRAInfo> lras = lraClient.getActiveLRAs();

        LRAInfo info = getLRA(lra);

        if (info != null) {
            assertEquals(CompensatorStatus.Completed.name(), info.getStatus(),
                    "delayEndLRA: LRA did not complete, status is " + info.getStatus(), resourcePath);
        }

        return PASSED_TEXT;
    }

    private String tryEndLRA(LRAId lraId, boolean failTest) {
        return tryEndLRA(false, lraId, 10000, true);
    }

    private String tryCancelLRA(LRAId lraId, boolean failTest) {
        return tryEndLRA(true, lraId, 10000, true);
    }

    private String tryEndLRA(boolean cancel, LRAId lraId, long maxMsecWait, boolean failTest) {
        if (maxMsecWait > 0) {
//            lraClient.connectTimeout(1000, TimeUnit.MILLISECONDS);
//            lraClient.readTimeout(maxMsecWait, TimeUnit.MILLISECONDS);
            ExecutorService executor = Executors.newCachedThreadPool();
            Callable<String> task = () -> cancel ? lraClient.cancelLRA(lraId) : lraClient.closeLRA(lraId);
            Future<String> future = executor.submit(task);

            try {
                return future.get(maxMsecWait, TimeUnit.MILLISECONDS);
            } catch (TimeoutException | InterruptedException | ExecutionException e) {
                if (failTest) {
                    throw new GenericLRAException(lraId, 0,
                            "delayEndLRA received unexpected exception: " + e.getMessage(), null);
                }
            } finally {
                future.cancel(true);
            }
        }

        return cancel ? lraClient.cancelLRA(lraId) : lraClient.closeLRA(lraId);
    }

    @Test
    public String connectionHangup() throws WebApplicationException {
        int[] cnt1 = {completedCount(ACTIVITIES_PATH, true),
                completedCount(ACTIVITIES_PATH, false)};

        List<LRAInfo> lras = lraClient.getActiveLRAs();
        int count = lras.size();
        LRAId lra = lraClient.startLRA(null, "SpecTest#connectionHangup",
                LRA_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        // tell the resource to generate a proccessing exception when asked to finish the LRA(simulates a connection hang)
        WebTarget resourcePath = msTarget.path(ACTIVITIES_PATH).path(ActivityController.WORK_RESOURCE_METHOD)
                .queryParam("how", "exception")
                .queryParam("arg", "javax.ws.rs.ProcessingException");
        response = resourcePath
                .request().header(LRAHttpClient.LRA_HTTP_HEADER, lra).put(Entity.text(""));
        checkStatusAndClose(response, Response.Status.OK.getStatusCode(), false, resourcePath);
        response = null;

        String status = lraClient.cancelLRA(lra);

        assertEquals(CompensatorStatus.Compensating.name(), status,
                "connectionHangup: canceled LRA should be compensating but is " + status, resourcePath);

        // check that participant was told to compensate
        int[] cnt2 = {completedCount(ACTIVITIES_PATH, true), completedCount(ACTIVITIES_PATH, false)};

        assertEquals(cnt1[0], cnt2[0], "connectionHangup: wrong completion count", resourcePath);
        assertEquals(cnt1[1] + 1, cnt2[1], "connectionHangup: wrong compensation count", resourcePath);

        // the coordinator should have received an exception so run recovery to force it to retry
        assertTrue(waitForRecovery(3, lra), "Still waiting for recovery after 3 attempts",
                resourcePath, lra);

        int[] cnt3 = {completedCount(ACTIVITIES_PATH, true), completedCount(ACTIVITIES_PATH, false)};

        // there should have been a second compensate call(from the recovery coordinator)
        assertEquals(cnt1[0], cnt3[0], "connectionHangup: wrong completion count after recovery", resourcePath);
        assertEquals(cnt1[1] + 2, cnt3[1], "connectionHangup: wrong compensation count after recovery",
                resourcePath);

        lras = lraClient.getActiveLRAs();
        System.out.printf("join ok %d versus %d lras%n", count, lras.size());
        assertEquals(count, lras.size(), "join: wrong LRA count", resourcePath);

        return PASSED_TEXT;
    }

    @Test
    public String getActiveLRAs() throws WebApplicationException {
        LRAId lra = lraClient.startLRA("SpecTest#getActiveLRAs", LRA_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        List<LRAInfo> lras = lraClient.getActiveLRAs();
        LRAInfo info = getLRA(lra);

        assertTrue(getLRA(lras, lra).isPresent(), "new LRA not active", null, lra);
        assertNotNull(info, "LRA is not active", null);

        return lraClient.cancelLRA(lra);
    }

    @Test
    public String getAllLRAs() throws WebApplicationException {
        LRAId lra = lraClient.startLRA("SpecTest#getAllLRAs", LRA_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        List<LRAInfo> lras = lraClient.getAllLRAs();
        LRAInfo info = getLRA(lra);

        assertTrue(getLRA(lras, lra).isPresent(), "new LRA not active", null, lra);
        assertNotNull(info, "LRA is not active", null);

        return lraClient.cancelLRA(lra);
    }

    @Test
    public String isActiveLRA() throws WebApplicationException {
        LRAId lra = lraClient.startLRA("SpecTest#isActiveLRA", LRA_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        assertTrue(lraClient.isActiveLRA(lra), "isActiveLRA: lra is not active", null, lra);

        return lraClient.cancelLRA(lra);
    }

    private void renewTimeLimit() {
        int[] cnt1 = {completedCount(ACTIVITIES_PATH, true), completedCount(ACTIVITIES_PATH, false)};
        response = null;

        try {
            WebTarget resourcePath = msTarget.path(ACTIVITIES_PATH)
                    .path("renewTimeLimit");

            response = resourcePath
                    .request()
                    .get();

            checkStatusAndClose(response, -1, true, resourcePath);
            response = null;

            // check that participant was invoked
            int[] cnt2 = {completedCount(ACTIVITIES_PATH, true), completedCount(ACTIVITIES_PATH, false)};

            /*
             * The call to activities/timeLimit should have started an LRA whch should not have timed out
             * (because the called resource method renews the timeLimit before sleeping for longer than
              * the @TimeLimit annotation specifies).
             * Therefore the it should not have compensated:
             */
            assertEquals(cnt1[0] + 1, cnt2[0],
                    resourcePath.getUri().toString() + ": compensate was called instead of complete", resourcePath);
            assertEquals(cnt1[1], cnt2[1],
                    resourcePath.getUri().toString() + ": compensate should not have been called", resourcePath);
        } finally {
            if (response != null) {
                response.close();
            }

            response = null;
        }
    }

    private String checkStatusAndClose(Response response, int expected, boolean readEntity, WebTarget webTarget) {
        try {
            if (expected != -1 && response.getStatus() != expected) {
                if (webTarget != null) {
                    throw new WebApplicationException(
                            String.format("%s wrong status code expected %d but received %d",
                            webTarget.getUri().toString(), expected, response.getStatus()),
                            response);
                }

                throw new WebApplicationException(response);
            }

            if (readEntity) {
                return response.readEntity(String.class);
            }
        } finally {
            response.close();
        }

        return null;
    }

    private int completedCount(String resourceBase, boolean completed) {
        response = null;
        String path = completed ? "completedactivitycount" : "compensatedactivitycount";

        try {
            WebTarget resourcePath = msTarget.path(resourceBase).path(path);

            response = resourcePath.request().get();

            assertEquals(Response.Status.OK.getStatusCode(),
                    response.getStatus(),
                    resourcePath.getUri().toString() + ": wrong status",
                    resourcePath);

            return Integer.parseInt(response.readEntity(String.class));
        } finally {
            if (response != null) {
                response.close();
            }

            response = null;
        }

    }

    private String multiLevelNestedActivity(CompletionType how, int nestedCnt) throws WebApplicationException {
        WebTarget resourcePath = msTarget.path(ACTIVITIES_PATH).path("multiLevelNestedActivity");

        int[] cnt1 = {completedCount(ACTIVITIES_PATH, true), completedCount(ACTIVITIES_PATH, false)};

        if (how == CompletionType.mixed && nestedCnt <= 1) {
            how = CompletionType.complete;
        }

        LRAId lra = lraClient.startLRA(null, "SpecTest#multiLevelNestedActivity", LRA_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        String lraId = lra.toString();

        response = resourcePath
                .queryParam("nestedCnt", nestedCnt)
                .request()
                .header(LRAHttpClient.LRA_HTTP_HEADER, lra)
                .put(Entity.text(""));

        String lraStr = checkStatusAndClose(response, Response.Status.OK.getStatusCode(), true, resourcePath);
        response = null;

        assert lraStr != null;
        String[] lraArray = lraStr.split(",");
        final List<LRAInfo> lras = lraClient.getActiveLRAs();
        LRAId[] urls = new LRAId[lraArray.length];

        IntStream.range(0, urls.length).forEach(i -> {
            try {
                urls[i] = new LRAUrl(lraArray[i]);
            } catch (InvalidLRAException e) {
                throw new GenericLRAException(lra, 0,
                        String.format("%s (multiLevelNestedActivity): returned an invalid URL: %s",
                                resourcePath.getUri().toString(), e.getMessage()), e);
            }
        });
        // check that the multiLevelNestedActivity method returned the mandatory LRA followed by two nested LRAs
        assertEquals(nestedCnt + 1, lraArray.length, "multiLevelNestedActivity: step 1", resourcePath);
        assertEquals(lraId, lraArray[0], "multiLevelNestedActivity: step 2", resourcePath); // first element should be the mandatory LRA

        // check that the coordinator knows about the two nested LRAs started by the multiLevelNestedActivity method
        // NB even though they should have completed they are held in memory pending the enclosing LRA finishing
        IntStream.rangeClosed(1, nestedCnt).forEach(i -> assertTrue(getLra(lras, lraArray[i]).isPresent(),
                "missing nested LRA",
                resourcePath, null));

        // and the mandatory lra seen by the multiLevelNestedActivity method
        assertTrue(getLra(lras, lraArray[0]).isPresent(), "lra should have been found", resourcePath, null);

        int[] cnt2 = {completedCount(ACTIVITIES_PATH, true), completedCount(ACTIVITIES_PATH, false)};

        // check that both nested activities were told to complete
        assertEquals(cnt1[0] + nestedCnt, cnt2[0], "multiLevelNestedActivity: step 3", resourcePath);
        // and that neither were told to compensate
        assertEquals(cnt1[1], cnt2[1], "multiLevelNestedActivity: step 4", resourcePath);

        // close the LRA
        if (how == CompletionType.compensate) {
            lraClient.cancelLRA(lra);
        } else if (how == CompletionType.complete) {
            lraClient.closeLRA(lra);
        } else {
            /*
             * The test is calling for a mixed outcome (a top level LRA L! and nestedCnt nested LRAs (L2, L3, ...)::
             * L1 the mandatory call (PUT "activities/multiLevelNestedActivity") registers participant C1
             *   the resource makes nestedCnt calls to "activities/nestedActivity" each of which create nested LRAs
             * L2, L3, ... each of which enlists a participant (C2, C3, ...) which are completed when the call returns
             * L2 is canceled  which causes C2 to compensate
             * L1 is closed which triggers the completion of C1
             *
             * To summarise:
             *
             * - C1 is completed
             * - C2 is completed and then compensated
             * - C3, ... are completed
             */
            lraClient.cancelLRA(urls[1]); // compensate the first nested LRA
            lraClient.closeLRA(lra); // should not complete any nested LRAs (since they have already completed via the interceptor)
        }

        // validate that the top level and nested LRAs are gone
        final List<LRAInfo> lras2 = lraClient.getActiveLRAs();

        IntStream.rangeClosed(0, nestedCnt).forEach(i -> assertTrue(!getLra(lras2, lraArray[i]).isPresent(),
                        "multiLevelNestedActivity: top level or nested activity still active",
                resourcePath, null));

        int[] cnt3 = {completedCount(ACTIVITIES_PATH, true), completedCount(ACTIVITIES_PATH, false)};

        if (how == CompletionType.complete) {
            // make sure that all nested activities were not told to complete or cancel a second time
            assertEquals(cnt2[0] + nestedCnt, cnt3[0], "multiLevelNestedActivity: step 5", resourcePath);
            // and that neither were still not told to compensate
            assertEquals(cnt1[1], cnt3[1], "multiLevelNestedActivity: step 6", resourcePath);

        } else if (how == CompletionType.compensate) {
            /*
             * the test starts LRA1 calls a @Mandatory method multiLevelNestedActivity which enlists in LRA1
             * multiLevelNestedActivity then calls an @Nested method which starts L2 and enlists another participant
             *   when the method returns the nested participant is completed (ie completed count is incremented)
             * Canceling L1 should then compensate the L1 enlistement (ie compensate count is incrememted)
             * which will then tell L2 to compenstate (ie the compensate count is incrememted again)
             */
            // each nested participant should have completed (the +nestedCnt)
            assertEquals(cnt1[0] + nestedCnt, cnt3[0], "multiLevelNestedActivity: step 7", resourcePath);
            // each nested participant should have compensated. The top level enlistement should have compensated (the +1)
            assertEquals(cnt2[1] + 1 + nestedCnt, cnt3[1], "multiLevelNestedActivity: step 8", resourcePath);
        } else {
            /*
             * The test is calling for a mixed uutcome:
             * - the top level LRA was closed
             * - one of the nested LRAs was compensated the rest should have been completed
             */
            // there should be just 1 compensation (the first nested LRA)
            assertEquals(1, cnt3[1] - cnt1[1], "multiLevelNestedActivity: step 9", resourcePath);
            /*
             * Expect nestedCnt + 1 completions, 1 for the top level and one for each nested LRA
             * (NB the first nested LRA is completed and compensated)
             * Note that the top level complete should not call complete again on the nested LRA
             */
            assertEquals(nestedCnt + 1, cnt3[0] - cnt1[0], "multiLevelNestedActivity: step 10", resourcePath);
        }

        return PASSED_TEXT;
    }

    private void cancelCheck(String path) {
        int[] cnt1 = {completedCount(ACTIVITIES_PATH, true), completedCount(ACTIVITIES_PATH, false)};
        LRAId lra = lraClient.startLRA(null, "SpecTest#" + path, LRA_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        response = null;

        WebTarget resourcePath = msTarget.path(ACTIVITIES_PATH).path(path);

        try {
            response = resourcePath
                    .request()
                    .header(LRAHttpClient.LRA_HTTP_HEADER, lra)
                    .get();

            checkStatusAndClose(response, Response.Status.BAD_REQUEST.getStatusCode(), true, resourcePath);
            response = null;

            // check that participant was invoked
            int[] cnt2 = {completedCount(ACTIVITIES_PATH, true), completedCount(ACTIVITIES_PATH, false)};

            // check that complete was not called and that compensate was
            assertEquals(cnt1[0], cnt2[0], "complete was called instead of compensate", resourcePath);
            assertEquals(cnt1[1] + 1, cnt2[1], "compensate should have been called", resourcePath);

            try {
                assertTrue(!lraClient.isActiveLRA(lra), "cancelCheck: LRA should have been cancelled", resourcePath, lra);
            } catch (NotFoundException ignore) {
                // means the LRA has gone
            }
        } finally {
            if (response != null) {
                response.close();
            }

            response = null;
        }
    }

    private boolean waitForRecovery(int noOfPasses, LRAId... lras) {
        String recoveryPath = System.getProperty(LRA_RECOVERY_PATH_KEY, "lra-recovery-coordinator");
        WebTarget resourcePath = recoveryTarget.path(recoveryPath).path("recovery");

        for (int i = 0; i < noOfPasses; i++) {
            // trigger a recovery scan to force a replay attempt on any pending participants
            response = resourcePath.request().get();

            String recoveringLRAs = checkStatusAndClose(response, Response.Status.OK.getStatusCode(),
                    true, resourcePath);
            response = null;

            int recoveredCnt = 0;

            assertNotNull(recoveringLRAs, "request for recovering LRAs produced a null value", resourcePath);

            for (LRAId lra : lras) {
                if (!recoveringLRAs.contains(lra.toString())) {
                    recoveredCnt += 1;
                }
            }

            if (recoveredCnt == lras.length) {
                return true;
            }
        }

        return false;
    }

    private Optional<LRAInfo> getLRA(List<LRAInfo> lras, LRAId lra) {
        return lras.stream()
                .filter(x -> x.getLraId().equals(lra.toString()))
                .findFirst();
    }

    private LRAInfo getLRA(LRAId lra) {
        response = null;

        try {
            response = msClient.target(lra.toString()).request().accept(MediaType.APPLICATION_JSON_TYPE).get();

            if (response.getStatus() != Response.Status.NOT_FOUND.getStatusCode()) {
                try {
                    if (response.hasEntity()) {
                        // read as a string since LRAInfo is an abstract type
                        String info = response.readEntity(String.class);
                        StringReader reader = new StringReader(info);
                        JsonReader jsonReader = Json.createReader(reader);
                        JsonObject jo = jsonReader.readObject();

                        return new LRAInfo() {
                            @Override
                            public String getLraId() {
                                try {
                                    return jo.getString("lraId");
                                } catch (InvalidLRAException e) {
                                    throw new InvalidLRAException(jo.getString("lraId"),
                                            "cannot convert LRA id",
                                            e);
                                }
                            }

                            @Override
                            public String getClientId() {
                                return jo.getString(STATUS_TEXT);
                            }

                            @Override
                            public boolean isComplete() {
                                return CompensatorStatus.Completed.name().equals(jo.getString(STATUS_TEXT));
                            }

                            @Override
                            public boolean isCompensated() {
                                return CompensatorStatus.Compensated.name().equals(jo.getString(STATUS_TEXT));
                            }

                            @Override
                            public boolean isRecovering() {
                                return jo.getBoolean("recovering");
                            }

                            @Override
                            public boolean isActive() {
                                return jo.getBoolean("active");
                            }

                            @Override
                            public boolean isTopLevel() {
                                return jo.getBoolean("topLevel");
                            }

                            @Override
                            public long getStartTime() {
                                return jo.getJsonNumber("startTime").longValue();
                            }

                            @Override
                            public long getFinishTime() {
                                return jo.getJsonNumber("finishTime").longValue();
                            }

                            @Override
                            public long getTimeNow() {
                                return jo.getJsonNumber("timeNow").longValue();
                            }

                            @Override
                            public ZoneOffset getZoneOffset() {
                                return ZoneOffset.UTC;
                            }

                            @Override
                            public String getStatus() {
                                return jo.getString(STATUS_TEXT);
                            }
                        };
                    }
                } catch (Exception ignore) {
                }
            }
        } finally {
            if (response != null) {
                response.close();
            }

            response = null;
        }

        return null;
    }

    private static Optional<LRAInfo> getLra(List<LRAInfo> lras, String lraId) {
        return lras.stream()
                .filter(x -> x.getLraId().equals(lraId))
                .findFirst();
    }

    private static void assertTrue(boolean condition, String reason, WebTarget target, LRAId lra) {
//        assert condition;

        if (!condition) {
            throw new GenericLRAException(lra, 0, target.getUri().toString() + ": " + reason, null);
        }
    }

    private static <T> void assertEquals(T expected, T actual, String reason, WebTarget target) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            if (target != null) {
                throw new GenericLRAException(null, 0, target.getUri().toString() + ": " + reason, null);
            }

            throw new GenericLRAException(null, 0, reason, null);
        }
    }

    private static <T> void assertNotNull(T value, String reason, WebTarget target) {
        if (value == null) {
            if (target == null) {
                throw new GenericLRAException(null, 0, reason, null);
            } else {
                throw new GenericLRAException(null, 0, target.getUri().toString() + reason, null);
            }
        }
    }

    private static <T> void assertNull(T value, String reason, WebTarget target) {
        if (value != null) {
            if (target == null) {
                throw new GenericLRAException(null, 0, reason, null);
            } else {
                throw new GenericLRAException(null, 0, target.getUri().toString() + reason, null);
            }
        }
    }
}
