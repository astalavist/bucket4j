/*
 * Copyright 2015 Vladimir Bukhtoyarov
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.github.bucket4j.local;


import com.github.bucket4j.*;

public class UnsafeBucket extends AbstractBucket {

    private final BucketState state;
    private final Bandwidth[] bandwidths;
    private final TimeMeter timeMeter;

    public UnsafeBucket(BucketConfiguration configuration) {
        super(configuration);
        this.bandwidths = configuration.getBandwidths();
        this.timeMeter = configuration.getTimeMeter();
        this.state = BucketState.createInitialState(configuration);
    }

    @Override
    protected long consumeAsMuchAsPossibleImpl(long limit) {
        long currentTimeNanos = timeMeter.currentTimeNanos();

        state.refillAllBandwidth(bandwidths, currentTimeNanos);
        long availableToConsume = state.getAvailableTokens(bandwidths);
        long toConsume = Math.min(limit, availableToConsume);
        if (toConsume == 0) {
            return 0;
        }
        state.consume(bandwidths, toConsume);
        return toConsume;
    }

    @Override
    protected boolean tryConsumeImpl(long tokensToConsume) {
        long currentTimeNanos = timeMeter.currentTimeNanos();

        state.refillAllBandwidth(bandwidths, currentTimeNanos);
        long availableToConsume = state.getAvailableTokens(bandwidths);
        if (tokensToConsume > availableToConsume) {
            return false;
        }
        state.consume(bandwidths, tokensToConsume);
        return true;
    }

    @Override
    protected boolean consumeOrAwaitImpl(long tokensToConsume, long waitIfBusyNanosLimit) throws InterruptedException {
        long currentTimeNanos = timeMeter.currentTimeNanos();

        state.refillAllBandwidth(bandwidths, currentTimeNanos);
        long nanosToCloseDeficit = state.delayNanosAfterWillBePossibleToConsume(bandwidths, tokensToConsume);
        if (nanosToCloseDeficit == 0) {
            state.consume(bandwidths, tokensToConsume);
            return true;
        }

        if (waitIfBusyNanosLimit > 0 && nanosToCloseDeficit > waitIfBusyNanosLimit) {
            return false;
        }

        state.consume(bandwidths, tokensToConsume);
        timeMeter.parkNanos(nanosToCloseDeficit);
        return true;
    }

    @Override
    protected void addTokensImpl(long tokensToAdd) {
        long currentTimeNanos = timeMeter.currentTimeNanos();
        state.refillAllBandwidth(bandwidths, currentTimeNanos);
        state.addTokens(bandwidths, tokensToAdd);
    }

    @Override
    public BucketState createSnapshot() {
        return state.clone();
    }

    @Override
    public String toString() {
        return "LockFreeBucket{" +
                "state=" + state +
                ", configuration=" + getConfiguration() +
                '}';
    }

}