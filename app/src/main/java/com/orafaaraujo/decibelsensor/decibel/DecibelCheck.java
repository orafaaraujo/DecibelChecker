package com.orafaaraujo.decibelsensor.decibel;

import android.annotation.SuppressLint;
import android.support.annotation.CheckResult;
import android.util.Log;

import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

public class DecibelCheck {

    private static final String TAG = "DecibelCheck";

    private static final int DECIBEL_THRESHOLD = 25;
    private static final int TIME_INTERVAL = 3;
    private static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;

    private boolean mIsRunning;
    private PublishSubject<Boolean> mPublishSubject;

    private DecibelSensor mDecibelSensor;

    public DecibelCheck() {
        mDecibelSensor = new DecibelSensor();
        mPublishSubject = PublishSubject.create();
    }

    @CheckResult
    public PublishSubject<Boolean> start() {
        Log.d(TAG, "start() called");
        if (!mIsRunning) {
            mDecibelSensor
                    .startObserving()
                    .buffer(TIME_INTERVAL, TIME_UNIT)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::makeAverage, this::showError, this::showCompleted);
            mIsRunning = true;
        }
        return mPublishSubject;
    }

    public void stop() {
        Log.d(TAG, "stop() called mIsRunning=" + mIsRunning);
        if (mIsRunning) {
            mDecibelSensor.stopObserving();
            mIsRunning = false;

            mPublishSubject.onComplete();
        }
    }

    private void makeAverage(List<Integer> decibelList) {
        Log.d(TAG, "makeAverage: " + decibelList);

        final OptionalDouble average = decibelList
                .stream()
                .mapToInt(value -> value)
                .average();

        average.ifPresent(value -> {
            Integer intAverage = Math.abs(Double.valueOf(value).intValue());
            logAverage(intAverage);
            if (intAverage < DECIBEL_THRESHOLD) {
                logAverage();
                mPublishSubject.onNext(true);
            }
        });
    }

    private void logAverage(Integer decibelData) {
        Log.d(TAG, "Decibel average: " + decibelData);
    }

    private void logAverage() {
        Log.d(TAG, "Decibel average passed! ");
    }

    private void showError(Throwable throwable) {
        Log.e(TAG, "Decibel check error: " + throwable.getLocalizedMessage());
    }

    @SuppressLint("SetTextI18n")
    private void showCompleted() {
        Log.i(TAG, "Decibel check completed!");
    }
}
