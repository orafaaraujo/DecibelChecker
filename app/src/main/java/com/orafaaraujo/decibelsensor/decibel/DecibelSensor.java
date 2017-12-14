package com.orafaaraujo.decibelsensor.decibel;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.support.annotation.CheckResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subjects.PublishSubject;

class DecibelSensor {

    private static final String TAG = "DecibelSensor";

    private static final int SAMPLE_RATE_IN_HZ = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private static final int TIME_INTERVAL = 3;
    private static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;

    private final int mBytesInBuffer;

    private AudioRecord mRecord;

    DecibelSensor() {
        mBytesInBuffer = AudioRecord
                .getMinBufferSize(SAMPLE_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT);
    }

    @CheckResult
    Single<List<Integer>> startObserving() {

        if (mBytesInBuffer < 0) {
            return sendError("Could not connect to microphone");
        }

        mRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT, mBytesInBuffer);

        if (mRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            return sendError("startObserving: Could not connect to microphone");
        }

        mRecord.startRecording();
        if (mRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            return sendError("startObserving: Microphone in use by another application");
        }

        return readRecord();
    }

    private Single<List<Integer>> readRecord() {
        PublishSubject<List<Integer>> publishSubject = PublishSubject.create();

        publishSubject
                .flatMap(v -> hearNoise());

        return publishSubject
                .single(new ArrayList<>());
    }

    void stopObserving() {
        if (mRecord != null) {
            if (mRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                mRecord.stop();
            }
            mRecord.release();
        }

        mRecord = null;
    }

    private Observable<Integer> hearNoise() {

        short[] tempBuffer = new short[mBytesInBuffer];

        return Observable
                .fromCallable(() -> {
                    int readShorts = mRecord.read(tempBuffer, 0, mBytesInBuffer);
                    if (readShorts > 0) {
                        return sendBuffer(tempBuffer, readShorts);
                    }
                    return 0;
                });
    }

    private int sendBuffer(short[] tempBuffer, int readShorts) {
        double totalSquared = 0;

        for (int i = 0; i < readShorts; i++) {
            short soundbits = tempBuffer[i];
            totalSquared += soundbits * soundbits;
        }

        // https://en.wikipedia.org/wiki/Sound_pressure
        final double quadraticMeanPressure = Math.sqrt(totalSquared / readShorts);
        final double uncalibratedDecibels = 20 * Math.log10(quadraticMeanPressure);

        if (isValidReading(uncalibratedDecibels)) {
            return Double.valueOf(uncalibratedDecibels).intValue();
        }
        return 0;
    }

    private boolean isValidReading(double reading) {
        return reading > -Double.MAX_VALUE;
    }

    private Single<List<Integer>> sendError(String message) {
        return Single.fromCallable(() -> {
            throw new RuntimeException(message);
        });
    }
}