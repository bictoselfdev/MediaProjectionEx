package com.example.mediaprojectionex

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.provider.MediaStore
import android.view.SurfaceView
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import io.reactivex.functions.Action
import io.reactivex.functions.Consumer
import java.text.SimpleDateFormat
import java.util.*

object MediaProjectionController {

    const val mediaScreenCapture = 100
    const val mediaScreenRecord = 101

    private var projectionManager: MediaProjectionManager? = null
    private var projectionCapture: MediaProjection? = null
    private var projectionRecord: MediaProjection? = null
    private var virtualDisplayCapture: VirtualDisplay? = null
    private var virtualDisplayRecord: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var mediaRecorder: MediaRecorder? = null

    private var width = 0
    private var height = 0

    private var prevIntentData: Intent? = null
    private var prevResultCode = 0

    private var captureCompletedAction: Consumer<Bitmap>? = null
    private var startRecordCompletedAction: Action? = null

    var isRecording = MutableLiveData(false)

    /***************************************************************************************
     * MediaProjection Screen Capture
     ***************************************************************************************/

    fun screenCapture(activity: Activity, action: Consumer<Bitmap>?) {
        captureCompletedAction = action

        if (prevIntentData != null) {
            // If you have received permission even once, proceed without requesting permission
            getMediaProjectionCapture(activity, prevResultCode, prevIntentData)
        } else {
            // permission request
            projectionManager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            activity.startActivityForResult(projectionManager?.createScreenCaptureIntent(), mediaScreenCapture)
        }
    }

    fun getMediaProjectionCapture(activity: Activity, resultCode: Int, intentData: Intent?) {
        projectionCapture = projectionManager?.getMediaProjection(resultCode, intentData!!)

        if (projectionCapture != null) {
            prevIntentData = intentData
            prevResultCode = resultCode

            // Create virtualDisplay
            createVirtualDisplayCapture(activity)
        }
    }

    private fun createVirtualDisplayCapture(activity: Activity) {
        val metrics = activity.resources?.displayMetrics!!
        val density = metrics.densityDpi
        val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC

        width = metrics.widthPixels
        height = metrics.heightPixels

        // called when there is a new image : OnImageAvailableListener
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener(ImageAvailableListener(), null)

        // ImageReader Surface rendering
        virtualDisplayCapture = projectionCapture?.createVirtualDisplay(
            "screenCapture", width, height, density, flags,
            imageReader?.surface, null, null
        )
    }

    private class ImageAvailableListener : OnImageAvailableListener {
        override fun onImageAvailable(reader: ImageReader) {
            var image: Image? = null
            try {
                image = reader.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    // Create bitmap
                    var bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(buffer)
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)

                    projectionCapture?.stop()

                    captureCompletedAction?.accept(bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                image?.close()
            }
        }
    }

    /***************************************************************************************
     * MediaProjection Screen Recording
     ***************************************************************************************/

    fun screenRecording(activity: Activity, action: Action?) {
        startRecordCompletedAction = action

        if (prevIntentData != null) {
            // If you have received permission even once, proceed without requesting permission
            getMediaProjectionRecord(activity, prevResultCode, prevIntentData)
        } else {
            // permission request
            projectionManager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            activity.startActivityForResult(projectionManager?.createScreenCaptureIntent(), mediaScreenRecord)
        }
    }

    fun getMediaProjectionRecord(activity: Activity, resultCode: Int, intentData: Intent?) {
        projectionRecord = projectionManager?.getMediaProjection(resultCode, intentData!!)

        if (projectionRecord != null) {
            prevIntentData = intentData
            prevResultCode = resultCode

            // Create virtualDisplay
            createVirtualDisplayRecord(activity)

            // MediaRecorder Start
            if (virtualDisplayRecord != null) {
                startRecording(activity)
            }
        }
    }

    private fun createVirtualDisplayRecord(activity: Activity) {
        val metrics = activity.resources?.displayMetrics!!
        val density = metrics.densityDpi
        val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR

        width = metrics.widthPixels
        height = metrics.heightPixels

        // MediaRecorder Prepare
        mediaRecorder = MediaRecorder()
        prepareRecording(activity)

        // MediaRecorder Surface rendering
        virtualDisplayRecord = projectionRecord?.createVirtualDisplay(
            "screenRecord", width, height, density, flags,
            mediaRecorder?.surface, null, null
        )
    }

    private fun prepareRecording(activity: Activity) {
        mediaRecorder?.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoEncodingBitRate(5 * 1024 * 1000)
            setVideoFrameRate(30)
            setVideoSize(width, height)

            val contentValues = ContentValues()
            val currentTime = Date(System.currentTimeMillis())
            val currentTimeStamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.KOREA).format(currentTime)
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "MediaProjectionEx$currentTimeStamp.mp4")
            val contentResolver = activity.contentResolver
            val collectionUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            val fileDescriptor = collectionUri?.let { contentResolver.openFileDescriptor(it, "w", null) }
            setOutputFile(fileDescriptor?.fileDescriptor)
            prepare()
        }
    }

    private fun startRecording(activity: Activity) {
        if (mediaRecorder != null) {
            try {
                mediaRecorder?.start()

                isRecording.value = true

                startRecordCompletedAction?.run()

                Toast.makeText(activity, "screenRecording...", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                System.err.println("[MediaProjection] start error : $e")
            }
        }
    }

    fun stopRecording(activity: Activity, action: Action?) {
        if (mediaRecorder != null) {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.reset()

                virtualDisplayRecord?.release()

                projectionRecord?.stop()

                isRecording.value = false

                action?.run()

                Toast.makeText(activity, "stopRecording, saved to public MediaStore", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                System.err.println("[MediaProjection] start error : $e")
            }
        }
    }
}