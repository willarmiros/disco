/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   or in the "license" file accompanying this file. This file is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *   express or implied. See the License for the specific language governing
 *   permissions and limitations under the License.
 */

package software.amazon.disco.agent.web.servlet;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import software.amazon.disco.agent.concurrent.TransactionContext;
import software.amazon.disco.agent.event.EventBus;
import software.amazon.disco.agent.event.HttpServletNetworkRequestEvent;
import software.amazon.disco.agent.event.HttpServletNetworkResponseEvent;
import software.amazon.disco.agent.interception.Installable;
import software.amazon.disco.agent.logging.LogManager;
import software.amazon.disco.agent.logging.Logger;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;

import java.util.concurrent.Callable;

/**
 * When the service() method of HttpServlet or subclass of it is called,
 * the method is intercepted to generate HttpNetworkProtocol(Request/Response)Events.
 */
public class HttpServletServiceInterceptor implements Installable {
    private final static Logger log = LogManager.getLogger(HttpServletServiceInterceptor.class);

    private static final String TX_NAMESPACE = "HTTP_SERVLET_SERVICE";
    private static final String EVENT_ORIGIN = "httpServlet";

    // Common HTTP Header keys
    private static final String DATE_HEADER = "date";
    private static final String HOST_HEADER = "host";
    private static final String ORIGIN_HEADER = "origin";
    private static final String REFERER_HEADER = "referer";
    private static final String USER_AGENT_HEADER = "user-agent";

    /**
     * {@inheritDoc}
     */
    @Override
    public AgentBuilder install(AgentBuilder agentBuilder) {
        return agentBuilder
                .type(buildClassMatcher())
                .transform((builder, typeDescription, classLoader, module) -> builder
                        .method(buildMethodMatcher())
                        .intercept(MethodDelegation.to(HttpServletServiceInterceptor.class)));
    }

    /**
     * The HttpServlet#service method is intercepted, and redirected here, where the
     * original request and response objects are sifted through to retrieve useful
     * header information that is stored in the HttpNetworkProtocol(Request/Response)Events
     * and published to the event bus.
     *
     * @param args    the original arguments passed to the invoke call
     * @param invoker the original 'this' of the invoker, in case useful or for debugging
     * @param origin  identifier of the intercepted method, for debugging/logging
     * @param zuper   a callable to call the original method
     * @throws Exception - catch-all for whatever exceptions might be throwable in the original call
     */
    @SuppressWarnings("unused")
    public static void service(@AllArguments Object[] args,
                               @This Object invoker,
                               @Origin String origin,
                               @SuperCall Callable<Object> zuper) throws Throwable {
        HttpServletNetworkRequestEvent requestEvent = null;
        HttpServletNetworkResponseEvent responseEvent = null;
        Throwable throwable = null;

        if (LogManager.isDebugEnabled()) {
            log.debug("DiSCo(Web) interception of " + origin);
        }

        if (TransactionContext.isWithinCreatedContext() && TransactionContext.getMetadata(TX_NAMESPACE) != null) {
            //since service() calls in subclasses may call their parents, this interceptor can stack up
            //only perform event publication it if we were the first call to take place
            zuper.call();
            return;
        }
        TransactionContext.create();
        TransactionContext.putMetadata(TX_NAMESPACE, true);

        try {
            // To reduce the # of dependencies, we use reflection to obtain the basic methods.
            Object request = args[0];
            HttpServletRequestAccessor reqAccessor = (HttpServletRequestAccessor) request;

            // Obtain the metadata information from the host.
            // If they are null, they are't stored, so retrieval would be null as well.
            int srcPort = reqAccessor.getRemotePort();
            int dstPort = reqAccessor.getLocalPort();
            String srcIP = reqAccessor.getRemoteAddr();
            String dstIP = reqAccessor.getLocalAddr();
            requestEvent = new HttpServletNetworkRequestEvent(EVENT_ORIGIN, srcPort, dstPort, srcIP, dstIP)
                        .withHeaderMap(reqAccessor.retrieveHeaderMap())
                        .withDate(reqAccessor.getHeader(DATE_HEADER))
                        .withHost(reqAccessor.getHeader(HOST_HEADER))
                        .withHTTPOrigin(reqAccessor.getHeader(ORIGIN_HEADER))
                        .withReferer(reqAccessor.getHeader(REFERER_HEADER))
                        .withUserAgent(reqAccessor.getHeader(USER_AGENT_HEADER))
                        .withMethod(reqAccessor.getMethod())
                        .withRequest(request)
                        .withURL(reqAccessor.getRequestUrl());
             EventBus.publish(requestEvent);
         } catch (Throwable e) {
            log.error("DiSCo(Web) Failed to retrieve request data from servlet service.");
         }

        // call the original, catching anything it throws
         try {
            zuper.call();
         } catch (Throwable t) {
            throwable = t;
         }

         try {
            Object response = args[1];
            HttpServletResponseAccessor respAccessor = (HttpServletResponseAccessor)response;

            int statusCode = respAccessor.getStatus();
            responseEvent = new HttpServletNetworkResponseEvent(EVENT_ORIGIN, requestEvent)
                        .withHeaderMap(respAccessor.retrieveHeaderMap())
                        .withStatusCode(statusCode)
                        .withResponse(response);
            EventBus.publish(responseEvent);
         } catch (Throwable t) {
                log.error("DiSCo(Web) Failed to retrieve response data from service.");
         }
        //match the create() call with a destroy() in all cases
        TransactionContext.destroy();
        //rethrow anything
        if (throwable != null) {
            throw throwable;
        }
    }

    /**
     * Build a ElementMatcher which defines the kind of class which will be intercepted. Package-private for tests.
     *
     * @return A ElementMatcher suitable to pass to the type() method of an AgentBuilder
     */
    ElementMatcher<? super TypeDescription> buildClassMatcher() {
        return ElementMatchers.hasSuperType(ElementMatchers.named("javax.servlet.http.HttpServlet"));
    }

    /**
     * Build an ElementMatcher which will match against the service() method of an HttpServlet.
     * Package-private for tests
     *
     * @return An ElementMatcher suitable for passing to the method() method of a DynamicType.Builder
     */
    ElementMatcher<? super MethodDescription> buildMethodMatcher() {
        ElementMatcher<? super TypeDescription> requestTypeName = ElementMatchers.named("javax.servlet.http.HttpServletRequest");
        ElementMatcher<? super TypeDescription> responseTypeName = ElementMatchers.named("javax.servlet.http.HttpServletResponse");
        ElementMatcher.Junction<? super MethodDescription> hasTwoArgs = ElementMatchers.takesArguments(2);
        ElementMatcher.Junction<? super MethodDescription> firstArgMatches = ElementMatchers.takesArgument(0, requestTypeName);
        ElementMatcher.Junction<? super MethodDescription> secondArgMatches = ElementMatchers.takesArgument(1, responseTypeName);
        ElementMatcher.Junction<? super MethodDescription> methodMatches = ElementMatchers.named("service").and(hasTwoArgs.and(firstArgMatches.and(secondArgMatches)));
        return methodMatches.and(ElementMatchers.not(ElementMatchers.isAbstract()));
    }
}
