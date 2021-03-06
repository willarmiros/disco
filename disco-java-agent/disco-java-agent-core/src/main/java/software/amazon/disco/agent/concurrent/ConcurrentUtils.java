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

package software.amazon.disco.agent.concurrent;

import software.amazon.disco.agent.event.EventBus;
import software.amazon.disco.agent.event.ThreadEnterEvent;
import software.amazon.disco.agent.event.ThreadExitEvent;
import software.amazon.disco.agent.logging.LogManager;
import software.amazon.disco.agent.logging.Logger;

import java.util.concurrent.ConcurrentMap;

/**
 * Utility methods for copying DiSCo propagation metadata, with checks for safety and redundancy.
 */
public class ConcurrentUtils {
    private static Logger log = LogManager.getLogger(ConcurrentUtils.class);

    /**
     * Propagate the transaction context, if running in a child of the ancestral thread
     * @param ancestralThreadId the threadId of the thread which created the TransactionContext for this family of threads
     * @param parentThreadId the threadId of the thread which created the object being passed across thread boundary
     * @param discoTransactionContext the parent's TransactionContext map.
     */
    public static void set(long ancestralThreadId, long parentThreadId, ConcurrentMap<String, MetadataItem> discoTransactionContext) {
        if (discoTransactionContext == null) {
            log.error("DiSCo(Core) could not propagate null context from thread id " + ancestralThreadId + " to thread id " + Thread.currentThread().getId());
            return;
        }

        long thisThreadId = Thread.currentThread().getId();
        if (ancestralThreadId != thisThreadId && !isDiscoNullId(discoTransactionContext)) {
            TransactionContext.setPrivateMetadata(discoTransactionContext);
            EventBus.publish(new ThreadEnterEvent("Concurrency", parentThreadId, Thread.currentThread().getId()));
        }
    }

    /**
     * Clear the transaction context, if running in a child of the ancestral thread
     * @param ancestralThreadId the threadId of the thread which created the TransactionContext for this family of threads
     * @param parentThreadId the threadId of the thread which created the object being passed across thread boundary
     * @param discoTransactionContext the parent's TransactionContext map.
     */
    public static void clear(long ancestralThreadId, long parentThreadId, ConcurrentMap<String, MetadataItem> discoTransactionContext) {
        if (discoTransactionContext == null) {
            return;
        }

        long thisThreadId = Thread.currentThread().getId();
        if (ancestralThreadId != thisThreadId && !isDiscoNullId(discoTransactionContext)) {
            EventBus.publish(new ThreadExitEvent("Concurrency", parentThreadId, Thread.currentThread().getId()));
            TransactionContext.clear();
        }
    }

    /**
     * Check if the incoming transaction context contains a null id, indicating that it is not parented to an
     * Activity/Request/Transaction - i.e. it might be background state such as a worker. In these situations
     * we don't issue events, or manage TransactionContext content.
     * @param discoTransactionContext the context to check
     * @return true if it is the sentinel value for a null ID, indicating a non-parented thread handoff.
     */
    private static boolean isDiscoNullId(ConcurrentMap<String, MetadataItem> discoTransactionContext) {
        MetadataItem item = discoTransactionContext.get(TransactionContext.TRANSACTION_ID_KEY);
        if (item == null) {
            return false;
        }
        return TransactionContext.getUninitializedTransactionContextValue().equals(item.get());
    }
}
