package com.fintechdev.zipkin;

import zipkin2.Call;
import zipkin2.CheckResult;
import zipkin2.codec.Encoding;
import zipkin2.reporter.Sender;
import zipkin2.reporter.urlconnection.URLConnectionSender;

import java.io.IOException;
import java.util.List;

/**
 * Envia o mesmo batch de spans para dois endpoints Zipkin (ex.: OpenZipkin + OpenTelemetry Collector).
 */
final class FanoutZipkinSender extends Sender {

    private final URLConnectionSender first;
    private final URLConnectionSender second;

    FanoutZipkinSender(URLConnectionSender first, URLConnectionSender second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public Encoding encoding() {
        return first.encoding();
    }

    @Override
    public int messageMaxBytes() {
        return Math.min(first.messageMaxBytes(), second.messageMaxBytes());
    }

    @Override
    public int messageSizeInBytes(List<byte[]> encodedSpans) {
        return first.messageSizeInBytes(encodedSpans);
    }

    @Override
    public Call<Void> sendSpans(List<byte[]> encodedSpans) {
        return new FanoutCall(encodedSpans);
    }

    private final class FanoutCall extends Call.Base<Void> {
        private final List<byte[]> encodedSpans;

        FanoutCall(List<byte[]> encodedSpans) {
            this.encodedSpans = encodedSpans;
        }

        @Override
        protected Void doExecute() throws IOException {
            first.sendSpans(encodedSpans).execute();
            second.sendSpans(encodedSpans).execute();
            return null;
        }

        @Override
        protected void doEnqueue(zipkin2.Callback<Void> callback) {
            try {
                callback.onSuccess(doExecute());
            } catch (Throwable t) {
                Call.propagateIfFatal(t);
                callback.onError(t);
            }
        }

        @Override
        @SuppressWarnings("MethodDoesntCallSuperMethod")
        public Call<Void> clone() {
            return new FanoutCall(encodedSpans);
        }
    }

    @Override
    public CheckResult check() {
        CheckResult a = first.check();
        CheckResult b = second.check();
        if (!a.ok()) {
            return a;
        }
        if (!b.ok()) {
            return b;
        }
        return CheckResult.OK;
    }

    @Override
    public void close() throws IOException {
        first.close();
        second.close();
    }
}
