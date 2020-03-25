/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.execution.dml;

import com.google.common.collect.Iterators;
import io.crate.expression.symbol.Symbol;
import io.crate.expression.symbol.Symbols;
import org.elasticsearch.action.support.replication.ReplicationRequest;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.lucene.uid.Versions;
import org.elasticsearch.index.seqno.SequenceNumbers;
import org.elasticsearch.index.shard.ShardId;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public abstract class ShardRequest<T extends ShardRequest<T, I>, I extends ShardRequest.Item>
    extends ReplicationRequest<T> implements Iterable<I> {

    private UUID jobId;
    protected List<I> items;

    @Nullable
    protected Symbol[] returnValues;

    protected boolean continueOnError;

    public ShardRequest() {
    }

    public ShardRequest(ShardId shardId, UUID jobId, boolean continueOnError) {
        setShardId(shardId);
        this.jobId = jobId;
        this.continueOnError = continueOnError;
        this.index = shardId.getIndexName();
        items = new ArrayList<>();
    }

    public void add(int location, I item) {
        item.location(location);
        items.add(item);
    }

    public List<I> items() {
        return items;
    }

    @Nullable
    public Symbol[] returnValues() {
        return returnValues;
    }

    public boolean continueOnError() {
        return continueOnError;
    }

    @Override
    public Iterator<I> iterator() {
        return Iterators.unmodifiableIterator(items.iterator());
    }

    public UUID jobId() {
        return jobId;
    }

    public ShardRequest(StreamInput in) throws IOException {
        super(in);
        jobId = new UUID(in.readLong(), in.readLong());
        continueOnError = in.readBoolean();
    }

    protected void readItems(StreamInput in, int size) throws IOException {
        items = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            items.add(readItem(in));
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeLong(jobId.getMostSignificantBits());
        out.writeLong(jobId.getLeastSignificantBits());
        out.writeBoolean(continueOnError);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ShardRequest<?, ?> that = (ShardRequest<?, ?>) o;
        return continueOnError == that.continueOnError &&
               Objects.equals(jobId, that.jobId) &&
               Objects.equals(items, that.items) &&
               Arrays.equals(returnValues, that.returnValues);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(jobId, items, continueOnError);
        result = 31 * result + Arrays.hashCode(returnValues);
        return result;
    }

    /**
     * The description is used when creating transport, replication and search tasks and it defaults to `toString`.
     * Only return the shard id to avoid the overhead of including all the items.
     */

    @Override
    public String toString() {
        return "ShardRequest{" +
               ", shardId=" + shardId +
               ", timeout=" + timeout +
               '}';
    }

    protected abstract I readItem(StreamInput input) throws IOException;

    public abstract static class Item implements Writeable {

        protected final String id;
        protected long version = Versions.MATCH_ANY;
        @Nullable
        protected BytesReference source;

        private int location = -1;
        protected long seqNo = SequenceNumbers.UNASSIGNED_SEQ_NO;
        protected long primaryTerm = SequenceNumbers.UNASSIGNED_PRIMARY_TERM;

        @Nullable
        protected Symbol[] returnValues;

        public Item(String id) {
            this.id = id;
        }

        protected Item(StreamInput in) throws IOException {
            id = in.readString();
            version = in.readLong();
            location = in.readInt();
            seqNo = in.readLong();
            primaryTerm = in.readLong();
            source = in.readBytesReference();
            int returnValueSize = in.readVInt();
            if (returnValueSize > 0) {
                returnValues = new Symbol[returnValueSize];
                for (int i = 0; i < returnValueSize; i++) {
                    returnValues[i] = Symbols.fromStream(in);
                }
            }
        }

        public String id() {
            return id;
        }

        public long version() {
            return version;
        }

        public void version(long version) {
            this.version = version;
        }

        public void location(int location) {
            this.location = location;
        }

        public int location() {
            return location;
        }

        public long seqNo() {
            return seqNo;
        }

        public void seqNo(long seqNo) {
            this.seqNo = seqNo;
        }

        public long primaryTerm() {
            return primaryTerm;
        }

        public void primaryTerm(long primaryTerm) {
            this.primaryTerm = primaryTerm;
        }

        public void source(BytesReference source) {
            this.source = source;
        }

        public BytesReference source() {
            return source;
        }

        @Nullable
        public Symbol[] returnValues() {
            return returnValues;
        }

        public void writeTo(StreamOutput out) throws IOException {
            out.writeString(id);
            out.writeLong(version);
            out.writeInt(location);
            out.writeLong(seqNo);
            out.writeLong(primaryTerm);
            out.writeBytesReference(source);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Item item = (Item) o;
            return version == item.version &&
                   location == item.location &&
                   seqNo == item.seqNo &&
                   primaryTerm == item.primaryTerm &&
                   java.util.Objects.equals(id, item.id) &&
                   java.util.Objects.equals(returnValues, item.returnValues) &&
                   java.util.Objects.equals(source, item.source);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(id, version, location, seqNo, primaryTerm, returnValues, source);
        }

        @Override
        public String toString() {
            return "Item{" +
                   "id='" + id + '\'' +
                   ", version=" + version +
                   ", location=" + location +
                   ", seqNo=" + seqNo +
                   ", primaryTerm=" + primaryTerm +
                   '}';
        }
    }

}
