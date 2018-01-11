package com.orafaaraujo.decibelsensor


import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.orafaaraujo.decibelsensor.decibel.DecibelCheck
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import java.util.logging.LogManager

class MainActivity() : AppCompatActivity() {

    companion object {
        const val TAG: String = "MainActivity"
        const val PERMISSION_CODE: Int = 1289
    }

    private var mDecibelCheck: DecibelCheck? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        main_start.setOnClickListener { start() }
    }

    private fun start() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startChecker()
        } else {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSION_CODE -> {
                if (grantResults.isNotEmpty()
                        && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    val intent = Intent()
                    intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } else {
                    startChecker()
                }
            }
        }
    }

    private fun startChecker() {
        LogManager.getLogManager()

        if (mDecibelCheck == null) {
            mDecibelCheck = DecibelCheck()
            mDecibelCheck?.let {
                it.start()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .distinctUntilChanged()
                        .takeUntil { b -> b }
                        .subscribe({ result ->
                            Log.d(TAG, "onNext: $result")
                            mDecibelCheck?.stop()
                        }, { error ->
                            Log.e(TAG, "onError {$error.message}")
                        })
            }
        }
    }
}
