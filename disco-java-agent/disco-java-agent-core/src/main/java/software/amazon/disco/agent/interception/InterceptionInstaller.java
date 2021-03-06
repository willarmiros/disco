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

package software.amazon.disco.agent.interception;

import software.amazon.disco.agent.config.AgentConfig;
import software.amazon.disco.agent.logging.LogManager;
import software.amazon.disco.agent.logging.Logger;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.lang.instrument.Instrumentation;
import java.util.Set;
import java.util.function.Supplier;

import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * Class to control installation of interceptions/advice on target methods.
 */
public class InterceptionInstaller {
    private static final InterceptionInstaller INSTANCE = new InterceptionInstaller(new DefaultAgentBuilderFactory());
    private static final Logger log = LogManager.getLogger(InterceptionInstaller.class);
    private final Supplier<AgentBuilder> agentBuilderFactory;

    /**
     * Non-public constructor for singleton semantics. Package-private for tests
     */
    InterceptionInstaller(Supplier<AgentBuilder> agentBuilderFactory) {
        this.agentBuilderFactory = agentBuilderFactory;
    }

    /**
     * Singleton access
     * @return the InterceptionInstaller singleton
     */
    public static InterceptionInstaller getInstance() {
        return INSTANCE;
    }

    /**
     * Sets up the interceptions as configured by the Agent, passing in a list of Installables.
     * @param instrumentation - the Instrumentation instance, as passed to 'premain'
     * @param installables - the collection of Installable hooks passed in from the Agent
     * @param config - the command line config passed into the agent.
     * @param customIgnoreMatcher extra ignore rules to be OR'd with the default
     */
    public void install(Instrumentation instrumentation, Set<Installable> installables, AgentConfig config,
                        ElementMatcher.Junction<? super TypeDescription> customIgnoreMatcher) {
        final ElementMatcher<? super TypeDescription> ignoreMatcher = createIgnoreMatcher(customIgnoreMatcher);

        for (Installable installable: installables) {
            //We create a new Agent for each Installable, otherwise their matching rules can
            //compete with each other.
            AgentBuilder agentBuilder = agentBuilderFactory.get()
                    .ignore(ignoreMatcher);

            //The Interception listener is expensive during class loading, and limited value most of the time
            if (config.isExtraverbose()) {
                agentBuilder = agentBuilder.with(InterceptionListener.create(installable));
            }

            agentBuilder = config.getAgentBuilderTransformer().apply(agentBuilder, installable);

            log.info("DiSCo(Core) attempting to install "+installable.getClass().getName());
            agentBuilder = installable.install(agentBuilder);

            if (agentBuilder != null) {
                agentBuilder.installOn(instrumentation);
            }
        }
    }

    /**
     * Create a matcher to ignore low-level and otherwise problematic namespaces.
     *
     * @param customIgnoreMatcher an extra ignore rule to be OR'd with the default
     * @return - a matcher suitable for passing to AgentBuilder#ignore
     */
    public static ElementMatcher.Junction<? super TypeDescription> createIgnoreMatcher(ElementMatcher.Junction<? super TypeDescription> customIgnoreMatcher) {
        ElementMatcher.Junction<? super TypeDescription> excludedNamespaces =
            //low-level pieces of the JDK and runtime
            nameStartsWith("sun.")
            .or(nameStartsWith("com.sun."))
            .or(nameStartsWith("jdk."))

            //3rd party libraries
            .or(nameStartsWith("org.jacoco."))
            .or(nameStartsWith("org.junit."))
            .or(nameStartsWith("org.aspectj."))

            //disco itself and its internals
            .or(nameStartsWith("software.amazon.disco.agent.")
                .and(not(nameStartsWith("software.amazon.disco.agent.integtest."))));

        return excludedNamespaces.or(customIgnoreMatcher);

    }

    /**
     * A default Factory for creation of AgentBuilder instances
     */
    private static class DefaultAgentBuilderFactory implements Supplier<AgentBuilder> {
        /**
         * Factory method to produce a real AgentBuilder
         * @return an AgentBuilder in the default case
         */
        @Override
        public AgentBuilder get() {
            return new AgentBuilder.Default();
        }
    }
}
