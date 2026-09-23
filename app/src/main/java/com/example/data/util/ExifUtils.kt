package com.example.data.util

import android.graphics.BitmapFactory
import android.media.ExifInterface
import java.io.File

data class ImageExifData(
    val dimensions: String? = null,
    val cameraModel: String? = null,
    val dateTaken: String? = null,
    val gpsCoordinates: String? = null,
    val hasGps: Boolean = false
)

object ExifUtils {

    fun readExifData(file: File): ImageExifData {
        var dimensions: String? = null
        var cameraModel: String? = null
        var dateTaken: String? = null
        var gpsCoordinates: String? = null
        var hasGps = false

        // 1. Dimensions via BitmapFactory without decoding full image
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                dimensions = "${options.outWidth} × ${options.outHeight} px"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. EXIF
        try {
            val exif = ExifInterface(file.absolutePath)
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)
            if (model != null) {
                cameraModel = if (make != null && !model.contains(make, ignoreCase = true)) "$make $model" else model
            }
            dateTaken = exif.getAttribute(ExifInterface.TAG_DATETIME)

            val latLong = FloatArray(2)
            if (exif.getLatLong(latLong)) {
                gpsCoordinates = String.format("%.5f, %.5f", latLong[0], latLong[1])
                hasGps = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ImageExifData(
            dimensions = dimensions,
            cameraModel = cameraModel,
            dateTaken = dateTaken,
            gpsCoordinates = gpsCoordinates,
            hasGps = hasGps
        )
    }

    fun removeLocationData(file: File): Boolean {
        return try {
            val exif = ExifInterface(file.absolutePath)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null)
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null)
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null)
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, null)
            exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null)
            exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, null)
            exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, null)
            exif.saveAttributes()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
