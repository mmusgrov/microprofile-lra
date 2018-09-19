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
package org.eclipse.microprofile.lra.tck.participant.api;

import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.CompensatorStatus;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.Forget;
import org.eclipse.microprofile.lra.annotation.LRA;
import org.eclipse.microprofile.lra.annotation.Leave;
import org.eclipse.microprofile.lra.annotation.NestedLRA;
import org.eclipse.microprofile.lra.annotation.Status;
import org.eclipse.microprofile.lra.annotation.TimeLimit;
import org.eclipse.microprofile.lra.client.GenericLRAException;
import org.eclipse.microprofile.lra.client.IllegalLRAStateException;
import org.eclipse.microprofile.lra.client.InvalidLRAIdException;
import org.eclipse.microprofile.lra.client.LRAClient;
import org.eclipse.microprofile.lra.client.LRAHttpClient;
import org.eclipse.microprofile.lra.client.LRAId;
import org.eclipse.microprofile.lra.client.http.LRAUrl;
import org.eclipse.microprofile.lra.tck.participant.model.Activity;
import org.eclipse.microprofile.lra.tck.participant.service.ActivityService;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

@ApplicationScoped
@Path(ActivityController.ACTIVITIES_PATH)
@LRA(LRA.Type.SUPPORTS)
public class ActivityController {
    public static final String ACTIVITIES_PATH = "activities";

    static final String MANDATORY_LRA_RESOURCE_PATH = "/mandatory";

    private static final String COMPLETE_RESOURCE_METHOD = "/complete";
    private static final String COMPENSATE_RESOURCE_METHOD = "/compensate";
    private static final String STATUS_RESOURCE_METHOD = "/status";
    private static final String FORGET_RESOURCE_METHOD = "/forget";
    private static final String SUPPORTS_RESOURCE_METHOD = "/supports";

    public static final String ACCEPT_WORK_RESOURCE_METHOD = "/acceptWork";
    public static final String LEAVE_RESOURCE_METHOD = "/leave";

    public static final String START_VIA_API_RESOURCE_METHOD = "/startViaApi";
    public static final String WORK_RESOURCE_METHOD = "/work";
    public static final String NESTED_ACTIVITY_RESOURCE_METHOD = "/nestedActivity";
    public static final String MULTI_LEVEL_NESTED_ACTIVITY_RESOURCE_METHOD = "/multiLevelNestedActivity";
    public static final String COMPLETED_COUNT_RESOURCE_METHOD = "/completedactivitycount";
    public static final String COMPENSATED_COUNT_RESOURCE_METHOD = "/compensatedactivitycount";
    public static final String CANCEL_ON_RESOURCE_METHOD = "/cancelOn";
    public static final String CANCEL_ON_FAMILY_RESOURCE_METHOD = "/cancelOnFamily";
    public static final String TIME_LIMIT_RESOURCE_METHOD = "/timeLimitRequiredLRA";
    public static final String RENEW_TIME_LIMIT_RESOURCE_METHOD = "/renewTimeLimit";
    public static final String END_TEST_RESOURCE_METHOD = "/cleanup";

    private static final Logger LOGGER = Logger.getLogger(ActivityController.class.getName());

    private static final AtomicInteger COMPLETED_COUNT = new AtomicInteger(0);
    private static final AtomicInteger COMPENSATED_COUNT = new AtomicInteger(0);

    @Inject
    private LRAClient lraClient;

    @Context
    private UriInfo context;

    @Inject
    private ActivityService activityService;

    /*
     * Performing a GET on the participant URL will return the current status of the participant
     * {@link CompensatorStatus}, or 404 if the participant is no longer present.
     */
    @GET
    @Path(STATUS_RESOURCE_METHOD)
    @Produces(MediaType.APPLICATION_JSON)
    @Status
    @LRA(LRA.Type.SUPPORTS) // remark: the status and forget methods should not start new LRAs
    public Response status(@HeaderParam(LRAHttpClient.LRA_HTTP_HEADER) String lraId) throws NotFoundException {
        Activity activity = activityService.getActivity(lraId);

        if (activity.getStatus() == null) {
            throw new IllegalLRAStateException(lraId, "LRA is not active", "getStatus");
        }

        if (activity.getAndDecrementAcceptCount() <= 0) {
            if (activity.getStatus() == CompensatorStatus.Completing) {
                activity.setStatus(CompensatorStatus.Completed);
            } else if (activity.getStatus() == CompensatorStatus.Compensating) {
                activity.setStatus(CompensatorStatus.Compensated);
            }
        }

        return Response.ok(activity.getStatus().name()).build();
    }

    /*
     * Test that participants can leave an LRA using the {@link LRAClient} programatic API
     * @param lraUrl the LRA that the participant should leave
     * @return the id of the LRA that was left
     * @throws NotFoundException if the requested LRA does not exist
     */
    @PUT
    @Path("/leave/{LraUrl}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response leaveWorkViaAPI(@PathParam("LraUrl")String lraUrl) throws NotFoundException, MalformedURLException {
        if (lraUrl != null) {
            // TODO this encoding of LRA URIs will be Narayana specific
            Map<String, String> terminateURIs =
                    Util.getTerminationUris(this.getClass(), context.getBaseUri());
            lraClient.leaveLRA(new LRAUrl(new URL(lraUrl)), terminateURIs.get("Link"));

            activityService.getActivity(lraUrl);

            activityService.remove(lraUrl);

            return Response.ok(lraUrl).build();
        }

        return Response.ok("non transactional").build();
    }

    @PUT
    @Path(LEAVE_RESOURCE_METHOD)
    @Produces(MediaType.APPLICATION_JSON)
    @Leave
    public Response leaveWork(@HeaderParam(LRAHttpClient.LRA_HTTP_HEADER) String lraId) throws NotFoundException {
        if (lraId != null) {
            activityService.getActivity(lraId);

            activityService.remove(lraId);

            return Response.ok(lraId).build();
        }

        return Response.ok("non transactional").build();
    }

    @PUT
    @Path(COMPLETE_RESOURCE_METHOD)
    @Produces(MediaType.APPLICATION_JSON)
    @Complete
    public Response completeWork(@HeaderParam(LRAHttpClient.LRA_HTTP_HEADER) String lraId, String userData) throws NotFoundException {
        COMPLETED_COUNT.incrementAndGet();

        assert lraId != null;

        Activity activity = activityService.getActivity(lraId);

        endCheck(activity); // call the end check before updating the end status

        activity.setEndData(userData);

        if (activity.getAndDecrementAcceptCount() > 0) {
            activity.setStatus(CompensatorStatus.Completing);
            activity.setStatusUrl(String.format("%s/%s/%s/status", context.getBaseUri(), ACTIVITIES_PATH, lraId));

            return Response.accepted().location(URI.create(activity.getStatusUrl())).build();
        }

        activity.setStatus(CompensatorStatus.Completed);
        activity.setStatusUrl(String.format("%s/%s/activity/completed", context.getBaseUri(), lraId));

        System.out.printf("ActivityController completing %s%n", lraId);
        return Response.ok(activity.getStatusUrl()).build();
    }

    @PUT
    @Path(COMPENSATE_RESOURCE_METHOD)
    @Produces(MediaType.APPLICATION_JSON)
    @Compensate
    public Response compensateWork(@HeaderParam(LRAHttpClient.LRA_HTTP_HEADER) String lraId, String userData) throws NotFoundException {
        COMPENSATED_COUNT.incrementAndGet();

        assert lraId != null;

        Activity activity = activityService.getActivity(lraId);

        endCheck(activity); // call the end check before updating the end status

        activity.setEndData(userData);

        if (activity.getAndDecrementAcceptCount() > 0) {
            activity.setStatus(CompensatorStatus.Compensating);
            activity.setStatusUrl(String.format("%s/%s/%s/status", context.getBaseUri(), ACTIVITIES_PATH, lraId));

            return Response.accepted().location(URI.create(activity.getStatusUrl())).build();
        }

        activity.setStatus(CompensatorStatus.Compensated);
        activity.setStatusUrl(String.format("%s/%s/activity/compensated", context.getBaseUri(), lraId));

        System.out.printf("ActivityController compensating %s%n", lraId);
        return Response.ok(activity.getStatusUrl()).build();
    }

    @DELETE
    @Path(FORGET_RESOURCE_METHOD)
    @Produces(MediaType.APPLICATION_JSON)
    @Forget
    @LRA(LRA.Type.SUPPORTS) // remark: the status and forget methods should not start new LRAs
    public Response forgetWork(@HeaderParam(LRAHttpClient.LRA_HTTP_HEADER) String lraId) { //throws NotFoundException {
        COMPLETED_COUNT.incrementAndGet();

        assert lraId != null;

        Activity activity = activityService.getActivity(lraId);

        endCheck(activity); // call the end check before updating the end status

        activityService.remove(activity.id);
        activity.setStatus(CompensatorStatus.Completed);
        activity.setStatusUrl(String.format("%s/%s/activity/completed", context.getBaseUri(), lraId));

        System.out.printf("ActivityController forgetting %s%n", lraId);
        return Response.ok(activity.getStatusUrl()).build();
    }

    @PUT
    @Path(ACCEPT_WORK_RESOURCE_METHOD)
    @LRA(LRA.Type.REQUIRED)
    public Response acceptWork(
            @HeaderParam(LRAHttpClient.LRA_HTTP_RECOVERY_HEADER) String rcvId,
            @HeaderParam(LRAHttpClient.LRA_HTTP_HEADER) String lraId) {
        assert lraId != null;
        Activity activity = addWork(lraId, rcvId);

        if (activity == null) {
            return Response.status(Response.Status.EXPECTATION_FAILED).entity("Missing lra data").build();
        }

        activity.setAcceptedCount(1); // tests that it is possible to asynchronously complete
        return Response.ok(lraId).build();
    }

    @PUT
    @Path(SUPPORTS_RESOURCE_METHOD)
    @LRA(LRA.Type.SUPPORTS)
    public Response supportsLRACall(@HeaderParam(LRAHttpClient.LRA_HTTP_HEADER) String lraId) {
        assert lraId != null;
        addWork(lraId, null);

        return Response.ok(lraId).build();
    }

    @PUT
    @Path(START_VIA_API_RESOURCE_METHOD)
    @LRA(LRA.Type.NOT_SUPPORTED)
    public Response subActivity(@HeaderParam(LRAHttpClient.LRA_HTTP_HEADER) String lraId) {
        if (lraId != null) {
            throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
        }

        // manually start an LRA via the injection LRAClient api
        LRAId lra = lraClient.startLRA("subActivity", 0L, TimeUnit.SECONDS);

        if (!(lra instanceof LRAUrl)) {
            throw new WebApplicationException("This TCK requires a JAX-RS implementation",
                    Response.Status.NOT_IMPLEMENTED);
        }

        lraId = lra.toString();

        addWork(lraId, null);

        // invoke a method that SUPPORTS LRAs. The filters should detect the LRA we just started via the injected client
        // and add it as a header before calling the method at path /supports (ie supportsLRACall()).
        // The supportsLRACall method will return LRA id in the body if it is present.
        String id = restPutInvocation((LRAUrl) lra, SUPPORTS_RESOURCE_METHOD, "");

        // check that the invoked method saw the LRA
        if (id == null || !lraId.equals(id)) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Entity.text("Unequal LRA ids")).build();
        }

        return Response.ok(id).build();
    }

    @PUT
    @Path(WORK_RESOURCE_METHOD)
    @LRA(LRA.Type.REQUIRED)
    public Response activityWithLRA(@HeaderParam(LRAHttpClient.LRA_HTTP_RECOVERY_HEADER) String rcvId,
                                    @HeaderParam(LRAHttpClient.LRA_HTTP_HEADER) String lraId,
                                    @QueryParam("how") String how,
                                    @QueryParam("arg") String arg) {
        assert lraId != null;
        Activity activity = addWork(lraId, rcvId);

        activity.setHow(how);
        activity.setArg(arg);

        return Response.ok(lraId).build();
    }

    @PUT
    @Path(END_TEST_RESOURCE_METHOD)
    @LRA(LRA.Type.SUPPORTS)
    public Response cleanUp(@QueryParam("how") String how, @QueryParam("arg") String arg) {
        activityService.findAll().forEach(activity -> activityService.remove(activity));

        return Response.ok().build();
    }

    @PUT
    @Path(MANDATORY_LRA_RESOURCE_PATH)
    @LRA(LRA.Type.MANDATORY)
    public Response activityWithMandatoryLRA(@HeaderParam(LRAHttpClient.LRA_HTTP_RECOVERY_HEADER) String rcvId,
                                    @HeaderParam(LRAHttpClient.LRA_HTTP_HEADER) String lraId,
                                    @QueryParam("how") String how,
                                    @QueryParam("arg") String arg) {
        return activityWithLRA(rcvId, lraId, how, arg);
    }

    private String restPutInvocation(LRAUrl lraURL, String path, String bodyText) {
        String id = null;
        Response response = ClientBuilder.newClient()
            .target(context.getBaseUri())
            .path("activities")
            .path(path)
            .request()
            .header(LRAHttpClient.LRA_HTTP_HEADER, lraURL)
            .put(Entity.text(bodyText));

        if (response.hasEntity()) {
            id = response.readEntity(String.class);
        }

        checkStatusAndClose(response, Response.Status.OK.getStatusCode());

        return id;
    }

    @PUT
    @Path(NESTED_ACTIVITY_RESOURCE_METHOD)
    @LRA(LRA.Type.MANDATORY)
    @NestedLRA
    public Response nestedActivity(@HeaderParam(LRAHttpClient.LRA_HTTP_RECOVERY_HEADER) String rcvId,
                                   @HeaderParam(LRAHttpClient.LRA_HTTP_HEADER) String nestedLRAId) {
        assert nestedLRAId != null;
        Activity activity = addWork(nestedLRAId, rcvId);

        if (activity == null) {
            return Response.status(Response.Status.EXPECTATION_FAILED).entity("Missing lra data").build();
        }

        return Response.ok(nestedLRAId).build();
    }

    @PUT
    @Path(MULTI_LEVEL_NESTED_ACTIVITY_RESOURCE_METHOD)
    @LRA(LRA.Type.MANDATORY)
    public Response multiLevelNestedActivity(
            @HeaderParam(LRAHttpClient.LRA_HTTP_RECOVERY_HEADER) String rcvId,
            @HeaderParam(LRAHttpClient.LRA_HTTP_HEADER) String nestedLRAId,
            @QueryParam("nestedCnt") @DefaultValue("1") Integer nestedCnt) {
        assert nestedLRAId != null;
        Activity activity = addWork(nestedLRAId, rcvId);

        if (activity == null) {
            return Response.status(Response.Status.EXPECTATION_FAILED).entity("Missing lra data").build();
        }

        LRAUrl lraURL;

        try {
            lraURL = new LRAUrl(new URL(URLDecoder.decode(nestedLRAId, "UTF-8")));
        } catch (MalformedURLException | UnsupportedEncodingException e) {
            throw new InvalidLRAIdException(nestedLRAId, e.getMessage(), e);
        }

        // invoke resources that enlist nested LRAs
        String[] lras = new String[nestedCnt + 1];
        lras[0] = nestedLRAId;
        IntStream.range(1, lras.length).forEach(i -> lras[i] = restPutInvocation(lraURL,"nestedActivity", ""));

        return Response.ok(String.join(",", lras)).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(LRA.Type.NOT_SUPPORTED)
    public Response findAll() {
        List<Activity> results = activityService.findAll();

        return Response.ok(results.size()).build();
    }

    @GET
    @Path(COMPLETED_COUNT_RESOURCE_METHOD)
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(LRA.Type.NOT_SUPPORTED)
    public Response getCompleteCount() {
        return Response.ok(COMPLETED_COUNT.get()).build();
    }

    @GET
    @Path(COMPENSATED_COUNT_RESOURCE_METHOD)
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(LRA.Type.NOT_SUPPORTED)
    public Response getCompensatedCount() {
        return Response.ok(COMPENSATED_COUNT.get()).build();
    }

    @GET
    @Path(CANCEL_ON_RESOURCE_METHOD)
    @Produces(MediaType.APPLICATION_JSON)
//    @LRA(value = LRA.Type.REQUIRED, cancelOn = {Response.Status.NOT_FOUND, Response.Status.BAD_REQUEST})
    @LRA(value = LRA.Type.REQUIRED, cancelOn = {NotFoundException.class})
    public Response cancelOn(@HeaderParam(LRAHttpClient.LRA_HTTP_HEADER) String lraId) {
        activityService.add(new Activity(lraId));//NarayanaLRAClient.getLRAId(lraId)));

        throw new NotFoundException("cancelOn test: Simulate buisiness logic failure");
//        return Response.status(Response.Status.BAD_REQUEST).entity(Entity.text("Simulate buisiness logic failure")).build();
    }

    @GET
    @Path(TIME_LIMIT_RESOURCE_METHOD)
    @Produces(MediaType.APPLICATION_JSON)
    @TimeLimit(limit = 100, unit = TimeUnit.MILLISECONDS)
    @LRA(value = LRA.Type.REQUIRED)
    public Response timeLimitRequiredLRA(@HeaderParam(LRAHttpClient.LRA_HTTP_HEADER) String lraId) {
        activityService.add(new Activity(lraId));//NarayanaLRAClient.getLRAId(lraId)));

        try {
            Thread.sleep(1000); // sleep for a period that is longer than specified in the @TimeLimit annotation
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }
        return Response.status(Response.Status.OK).entity(Entity.text("Simulate buisiness logic timeoout")).build();
    }

    @GET
    @Path(RENEW_TIME_LIMIT_RESOURCE_METHOD)
    @Produces(MediaType.APPLICATION_JSON)
    @TimeLimit(limit = 100, unit = TimeUnit.MILLISECONDS)
    @LRA(value = LRA.Type.REQUIRED)
    public Response extendTimeLimit(@HeaderParam(LRAHttpClient.LRA_HTTP_HEADER) String lraId) {
        activityService.add(new Activity(lraId));//NarayanaLRAClient.getLRAId(lraId)));

        try {
            /*
             * the incomming LRA was created with a timeLimit of 100 ms via the @TimeLimit annotation
             * update the timeLimit to 300
             * sleep for 200
             * return from the method so the LRA will have been running for 200 ms so it should not be cancelled
             */
            lraClient.renewTimeLimit(new LRAUrl(lraToURL(lraId, "Invalid LRA id")), 300, TimeUnit.MILLISECONDS);
            Thread.sleep(200); // sleep for 200000 micro seconds (should be longer than specified in the @TimeLimit annotation)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }
        return Response.status(Response.Status.OK).entity(Entity.text("Simulate buisiness logic timeoout")).build();
    }

    /*
     * Performing a PUT on <participant URL>/compensate will cause the participant to compensate
     * the work that was done within the scope of the transaction.
     *
     * The participant will either return a 200 OK code and a <status URL> which indicates the outcome and which can be probed (via GET)
     * and will simply return the same (implicit) information:
     *
     * <URL>/cannot-compensate
     * <URL>/cannot-complete
     */
    @PUT
    @Path("/{TxId}/compensate")
    @Produces(MediaType.APPLICATION_JSON)
    public Response compensate(@PathParam("TxId")String txId) throws NotFoundException {
        Activity activity = activityService.getActivity(txId);

        endCheck(activity);

        activity.setStatus(CompensatorStatus.Compensated);
        activity.setStatusUrl(String.format("%s/%s/activity/compensated", context.getBaseUri(), txId));

        return Response.ok(activity.getStatusUrl()).build();
    }

    /*
     * Performing a PUT on <participant URL>/complete will cause the participant to tidy up and it can forget this transaction.
     *
     * The participant will either return a 200 OK code and a <status URL> which indicates the outcome and which can be probed (via GET)
     * and will simply return the same (implicit) information:
     * <URL>/cannot-compensate
     * <URL>/cannot-complete
     */
    @PUT
    @Path("/{TxId}/complete")
    @Produces(MediaType.APPLICATION_JSON)
    public Response complete(@PathParam("TxId")String txId) throws NotFoundException {
        Activity activity = activityService.getActivity(txId);

        endCheck(activity);

        activity.setStatus(CompensatorStatus.Completed);
        activity.setStatusUrl(String.format("%s/%s/activity/completed", context.getBaseUri(), txId));

        return Response.ok(activity.getStatusUrl()).build();
    }

    @PUT
    @Path("/{TxId}/forget")
    public void forget(@PathParam("TxId")String txId) throws NotFoundException {
        Activity activity = activityService.getActivity(txId);

        endCheck(activity);

        activityService.remove(activity.id);
    }

    @GET
    @Path("/{TxId}/completed")
    @Produces(MediaType.APPLICATION_JSON)
    public String completedStatus(@PathParam("TxId")String txId) {
        return CompensatorStatus.Completed.name();
    }

    @GET
    @Path("/{TxId}/compensated")
    @Produces(MediaType.APPLICATION_JSON)
    public String compensatedStatus(@PathParam("TxId")String txId) {
        return CompensatorStatus.Compensated.name();
    }

    private Activity addWork(String lraId, String rcvId) {
        assert lraId != null;

        System.out.printf("ActivityController: work id %s and rcvId %s %n", lraId, rcvId);

        try {
            return activityService.getActivity(lraId);
        } catch (NotFoundException e) {
            Activity activity = new Activity(lraId);

            activity.setRcvUrl(rcvId);
            activity.setStatus(null);

            activityService.add(activity);

            return activity;
        }
    }

    private void checkStatusAndClose(Response response, int expected) {
        try {
            if (response.getStatus() != expected) {
                throw new WebApplicationException(response);
            }
        } finally {
            response.close();
        }
    }

    private void endCheck(Activity activity) {
        String how = activity.getHow();
        String arg = activity.getArg();

        activity.setHow(null);
        activity.setArg(null);

        if ("wait".equals(how)) {
            if (arg != null) {
                if ("recovery".equals(arg)) {
                    /*
                     * during end processing we delay the response by triggering a recovery scan
                     * which tests that the coordinator can handle slow participants and is able
                     * to tolerate recovery running when there are outstanding participant
                     * completion calls.
                     */
                    lraClient.getRecoveringLRAs(); // run a recovery scan
                } else {
                    int ms = 0;

                    try {
                        ms = Integer.getInteger(arg, 0);
                    } catch (Exception ignore) {
                    }

                    activity.waitFor(ms <= 0 ? Long.MAX_VALUE : ms); // delay the end call
                }
            }
        } else if ("exception".equals(how)) {
            Exception cause = null;

            if (arg != null) {
                try {
                    cause = (Exception) Class.forName(arg).newInstance();
                } catch (ClassNotFoundException | IllegalAccessException | InstantiationException ignore) {
                }
            }

            // throwing an exception during end should cause the status method to be consulted
            throw new ProcessingException("*** SIMULATING CONNECTION CLOSED ...", cause);
        }
    }

    private static URL lraToURL(String lraId, String errorMessage) {
        try {
            return new URL(lraId);
        } catch (MalformedURLException e) {
            LOGGER.log(Level.WARNING, "Can't construct URL from LRA id " + lraId);

            throw new GenericLRAException(null, BAD_REQUEST.getStatusCode(), errorMessage + ": lra id: " + lraId, e);
        }
    }
}
