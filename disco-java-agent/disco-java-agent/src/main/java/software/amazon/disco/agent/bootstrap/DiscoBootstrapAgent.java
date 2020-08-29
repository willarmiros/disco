/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package software.amazon.disco.agent.bootstrap;

import software.amazon.disco.agent.DiscoAgentTemplate;
import software.amazon.disco.agent.concurrent.ConcurrencySupport;
import software.amazon.disco.agent.config.AgentConfig;
import software.amazon.disco.agent.logging.LogManager;
import software.amazon.disco.agent.logging.Logger;
import software.amazon.disco.agent.plugin.PluginOutcome;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * Canonical 'empty' agent used as a vessel for discovered plugins. Unless building a monolithic agent for a site-specific
 * purpose, it is usually desirable to have a singular disco agent acting as a substrate, with lightweight discoverable plugins
 * containing interception treatments expressed as Installables (see disco-java-agent-web-plugin for an example), and implementation
 * of products (such as loggers, or context propagation features for your site) in the form of Listeners.
 *
 * It is expected that the agent jar file is configured with an appropriate Manifest, specifying the Boot-Class-Path accordingly
 * since the core concurrency treatments are inoperable otherwise. See this module's build.gradle.kts.
 */
public class DiscoBootstrapAgent {
    private static Logger log = LogManager.getLogger(DiscoBootstrapAgent.class);

    /**
     * Common implementation delegated to by both the premain() and agentmain() methods.
     *
     * @param agentArgs arguments which are passed to disco's core for configuration. Generally speaking this necessitates
     *                  passing a 'pluginPath' argument, so that disco knows where to find its extensions. Without this argument
     *                  the agent performs thread hand-off housekeeping, but is otherwise a no-op. See AgentConfigParser for
     *                  other available options
     * @param instrumentation an Instrumentation instance provided by the Java runtime, to allow bytecode manipulations
     */
    public static void initialize(String agentArgs, Instrumentation instrumentation) {
        log.info("DiSCo(Agent) starting agent");
        DiscoAgentTemplate agent = new DiscoAgentTemplate(agentArgs);

        AgentConfig config = agent.getConfig();
        if (config.getPluginPath() == null) {
            log.warn("DiSCo(Agent) no pluginPath configured, agent is effectively inert. Are you sure that's what you intended?");
        }

        //if forking this canonical agent to build a site specific variant, you may wish to provide a custom ignore-matcher
        //to the install() method, if you have commonly used internal software which benefits from being 'avoided' by the
        //installed interceptions. e.g.:

        //ElementMatcher myIgnoreMatcher = ElementMatchers.named("com.my.organization.some.problematic.class.TheClass");
        Collection<PluginOutcome> outcomes = agent.install(instrumentation, new HashSet<>(new ConcurrencySupport().get())/*, myIgnoreMatcher */);
        dump(outcomes);

        log.info("DiSCo(Agent) agent startup complete");
    }

    /**
     * As a debugging courtesy, we dump to any configured Logger what the agent installed and discovered. By default
     * no logger is installed unless either specified on the command line, or explicitly given by a custom fork of this
     * agent code.
     *
     * @param outcomes the summary of outcomes produced by the Plugin Discovery subsystem.
     */
    private static void dump(Collection<PluginOutcome> outcomes) {
        for (PluginOutcome outcome: outcomes) {
            StringBuilder builder = new StringBuilder();
            builder.append("DiSCo(Agent) Plugin name: ").append(outcome.name).append("\n");

            builder.append("\tBootstrap: ").append(outcome.bootstrap ? "yes" : "no").append("\n");

            if (outcome.initClass != null) {
                builder.append("\tInit: ").append(outcome.initClass.getName()).append("\n");
            }

            if (outcome.installables != null && !outcome.installables.isEmpty()) {
                List<String> installableStrings = new ArrayList<>(outcome.installables.size());
                outcome.installables.forEach(i -> installableStrings.add(i.getClass().getName()));
                builder.append("\tInstallables: ").append(String.join(", ", installableStrings.toArray(new String[0]))).append("\n");
            }

            if (outcome.listeners != null && !outcome.listeners.isEmpty()) {
                List<String> listenerStrings = new ArrayList<>(outcome.listeners.size());
                outcome.listeners.forEach(l -> listenerStrings.add(l.getClass().getName()));
                builder.append("\tListeners: ").append(String.join(", ", listenerStrings.toArray(new String[0]))).append("\n");
            }

            log.info(builder.toString());
        }
    }
}
