package com.orafaaraujo.decibelsensor.decibel;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.support.annotation.CheckResult;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.subjects.PublishSubject;

class DecibelSensor {

    private static final int SAMPLE_RATE_IN_HZ = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private final int mBytesInBuffer;
    private final ExecutorService mExecutor;
    private final AtomicBoolean mRunning = new AtomicBoolean(false);

    private AudioRecord mRecord;
    private PublishSubject<Integer> mStream;

    DecibelSensor() {
        mBytesInBuffer = AudioRecord
                .getMinBufferSize(SAMPLE_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT);
        mExecutor = Executors.newSingleThreadExecutor();
    }

    @CheckResult
    PublishSubject<Integer> startObserving() {

        mStream = PublishSubject.create();

        if (mBytesInBuffer < 0) {
            sendError("Could not connect to microphone");
            return mStream;
        }

        mRunning.set(true);
        mRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT, mBytesInBuffer);

        if (mRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            sendError("startObserving: Could not connect to microphone");
            return mStream;
        }

        mRecord.startRecording();
        if (mRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            sendError("startObserving: Microphone in use by another application");
            return mStream;
        }

        readRecord();

        return mStream;
    }

    private void readRecord() {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                short[] tempBuffer = new short[mBytesInBuffer];

                while (mRunning.get()) {
                    int readShorts = mRecord.read(tempBuffer, 0, mBytesInBuffer);
                    if (readShorts > 0) {
                        sendBuffer(tempBuffer, readShorts);
                    }
                }
            }

            private void sendBuffer(short[] tempBuffer, int readShorts) {
                double totalSquared = 0;

                for (int i = 0; i < readShorts; i++) {
                    short soundbits = tempBuffer[i];
                    totalSquared += soundbits * soundbits;
                }

                // https://en.wikipedia.org/wiki/Sound_pressure
                final double quadraticMeanPressure = Math.sqrt(totalSquared / readShorts);
                final double uncalibratedDecibels = 20 * Math.log10(quadraticMeanPressure);

                if (isValidReading(uncalibratedDecibels)) {
                    sendData(Double.valueOf(uncalibratedDecibels).intValue());
                }
            }
        });
    }

    void stopObserving() {
        mRunning.set(false);
        if (mRecord != null) {
            if (mRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                mRecord.stop();
            }
            mRecord.release();
        }
        mStream.onComplete();

        mRecord = null;
        mStream = null;
    }

    private boolean isValidReading(double reading) {
        return reading > -Double.MAX_VALUE;
    }

    private void sendData(Integer decibelData) {
        mStream.onNext(decibelData);
    }

    private void sendError(String message) {
        mStream.onError(new Throwable(message));
    }
}