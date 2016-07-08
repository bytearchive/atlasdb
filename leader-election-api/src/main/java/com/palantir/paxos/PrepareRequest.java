/*
 * Copyright 2016 Palantir Technologies
 * ​
 * Licensed under the BSD-3 License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * ​
 * http://opensource.org/licenses/BSD-3-Clause
 * ​
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.paxos;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(as = ImmutablePrepareRequest.class)
@JsonDeserialize(as = ImmutablePrepareRequest.class)
@Value.Immutable
public abstract class PrepareRequest {
    public abstract PaxosInstanceId getKey();

    public abstract PaxosProposalId getPid();

    public static PrepareRequest from(PaxosInstanceId key, PaxosProposalId pid) {
        return ImmutablePrepareRequest.builder().key(key).pid(pid).build();

    }
}