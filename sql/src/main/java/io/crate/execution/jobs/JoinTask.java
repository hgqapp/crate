/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.execution.jobs;

import io.crate.concurrent.CompletionListenable;
import io.crate.execution.dsl.phases.JoinPhase;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

class JoinTask implements Task, DownstreamRXTask {

    private final JoinPhase joinPhase;

    @Nullable
    private final PageBucketReceiver leftPageBucketReceiver;

    @Nullable
    private final PageBucketReceiver rightPageBucketReceiver;
    private final CompletableFuture<?> resultFuture;

    JoinTask(JoinPhase joinPhase,
             CompletionListenable<?> completionListenable,
             @Nullable PageBucketReceiver leftPageBucketReceiver,
             @Nullable PageBucketReceiver rightPageBucketReceiver) {
        this.joinPhase = joinPhase;
        this.leftPageBucketReceiver = leftPageBucketReceiver;
        this.rightPageBucketReceiver = rightPageBucketReceiver;
        this.resultFuture = completionListenable.completionFuture();
    }

    @Override
    public void start() {
    }

    @Override
    public String name() {
        return joinPhase.name();
    }

    @Override
    public int id() {
        return joinPhase.phaseId();
    }

    @Override
    public PageBucketReceiver getBucketReceiver(byte inputId) {
        assert inputId < 2 : "Only 0 and 1 inputId's supported";
        if (inputId == 0) {
            return leftPageBucketReceiver;
        }
        return rightPageBucketReceiver;
    }

    @Override
    public String toString() {
        return "JoinTask{" +
               "id=" + id() +
               ", leftCtx=" + leftPageBucketReceiver +
               ", rightCtx=" + rightPageBucketReceiver +
               '}';
    }

    @Override
    public CompletableFuture<CompletionState> completionFuture() {
        return resultFuture.thenApply(ignored -> new CompletionState(0));
    }

    @Override
    public void kill(@Nonnull Throwable throwable) {
        resultFuture.completeExceptionally(throwable);
    }
}
