package com.orafaaraujo.decibelsensor.decibel;

import android.annotation.SuppressLint;
import android.support.annotation.CheckResult;
import android.util.Log;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

public class DecibelCheck {

    private static final String TAG = "DecibelCheck";

    private static final int DECIBEL_THRESHOLD = 30;
    private static final int TIME_INTERVAL = 3;
    private static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;

    private PublishSubject<Boolean> mPublishSubject;
    private Disposable mDisposable;

    private DecibelSensor mDecibelSensor;

    public DecibelCheck() {
        mDecibelSensor = new DecibelSensor();
        mPublishSubject = PublishSubject.create();
    }

    @CheckResult
    public PublishSubject<Boolean> start() {
        Log.d(TAG, "start() called");

        mDisposable = mDecibelSensor
                .startObserving()
                .buffer(TIME_INTERVAL, TIME_UNIT)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::makeAverage, this::showError, this::showCompleted);
        return mPublishSubject;
    }

    public void stop() {
        Log.d(TAG, "stop() called");

        mDecibelSensor.stopObserving();
        mPublishSubject.onComplete();
        mDisposable.dispose();
    }

    private void makeAverage(List<Integer> decibelList) {
        Log.d(TAG, "makeAverage() called with: decibelList = [" + decibelList + "]");

        decibelList
                .stream()
                .mapToInt(value -> value)
                .average()
                .ifPresent(value -> {
                    final int intAverage = Math.abs(Double.valueOf(value).intValue());
                    final boolean passed = intAverage > DECIBEL_THRESHOLD;
                    logAverage(intAverage, passed);
                    if (passed) {
                        mPublishSubject.onNext(true);
                    }
                });
    }

    private void logAverage(Integer intAverage, boolean passed) {
        Log.d(TAG,
                "logAverage() called with: intAverage = [" + intAverage + "], passed = [" + passed
                        + "]");
    }

    private void showError(Throwable throwable) {
        Log.d(TAG,
                "showError() called with: throwable = [" + throwable.getLocalizedMessage() + "]");
    }

    @SuppressLint("SetTextI18n")
    private void showCompleted() {
        Log.d(TAG, "showCompleted() called");
    }
}
