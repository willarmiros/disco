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
package software.amazon.disco.agent.integtest.concurrent.source;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Factory to create a ScheduledThreadPoolExecutor with a pool size of 2. A pool size >1 may be required for tests that ensure a thread
 * hand-off occurred, see {@link software.amazon.disco.agent.integtest.concurrent.source.TestableConcurrencyObjectImpl#testAfterConcurrentInvocation()}
 */
public class ScheduledThreadPoolExecutorFactory implements ExecutorServiceFactory {
    @Override
    public ExecutorService createExecutorService() {
        return new ScheduledThreadPoolExecutor(2);
    }
}
