package com.example.mediaprojectionex

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import com.example.mediaprojectionex.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        permissionCheck()
        startMainService()
        initializeView()
        setListener()
    }

    override fun onDestroy() {
        super.onDestroy()

        stopMainService()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            MediaProjectionController.mediaScreenCapture -> {
                MediaProjectionController.getMediaProjectionCapture(this, resultCode, data)
            }
            MediaProjectionController.mediaScreenRecord -> {
                MediaProjectionController.getMediaProjectionRecord(this, resultCode, data)
            }
        }
    }

    private fun startMainService() {

        val serviceIntent = Intent(this, MainService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopMainService() {

        val serviceIntent = Intent(this, MainService::class.java)
        stopService(serviceIntent)
    }

    private fun initializeView() {

        MediaProjectionController.isRecording.observe(this) { isRecording ->
            if (isRecording) {
                binding.btnRecordStart.isEnabled = false
                binding.btnRecordStop.isEnabled = true
            } else {
                binding.btnRecordStart.isEnabled = true
                binding.btnRecordStop.isEnabled = false
            }
        }
    }

    private fun setListener() {

        binding.btnCapture.setOnClickListener {
            MediaProjectionController.screenCapture(this) { bitmap ->
                // TODO : You can use the captured image (bitmap)

                binding.imgView.setImageBitmap(bitmap) // Example to show the result

                MainApplication.updateNotification(this, "Capture")
                Toast.makeText(this, "screenCapture completed.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRecordStart.setOnClickListener {
            MediaProjectionController.screenRecording(this) {
                // TODO : Action after start recording

                MainApplication.updateNotification(this, "Recording", "Start")
            }
        }

        binding.btnRecordStop.setOnClickListener {
            MediaProjectionController.stopRecording(this) {
                // TODO : Action after stop recording

                MainApplication.updateNotification(this, "Recording", "Stop")
            }
        }
    }

    private fun permissionCheck(): Boolean {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ), 300
                )
                return false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

        return true
    }
}