/*
 * Copyright (c) 2019 ThanksMister LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thanksmister.iot.wallpanel.modules

import android.util.SparseArray

import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.Frame
import com.thanksmister.iot.wallpanel.modules.Motion.Companion.MOTION_DETECTED
import com.thanksmister.iot.wallpanel.modules.Motion.Companion.MOTION_NOT_DETECTED
import com.thanksmister.iot.wallpanel.modules.Motion.Companion.MOTION_TOO_DARK

import timber.log.Timber
import java.nio.IntBuffer
import android.graphics.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Created by Michael Ritchie on 7/6/18.
 */
class MotionDetector private constructor(private val minLuma: Int, private val motionLeniency: Int) : Detector<Motion>() {
    private val imageComparator = ImageCompare(64)
    private val continousMovementDetection = ContinousMotionDetection(this.imageComparator)
    private var frameCounter = 0

    private fun frameToBitmap(frame: Frame): Bitmap {
        val pixels = probeFrame(frame)
        //val bitmap = byteBufferToBitmap(pixels, frame.metadata.width/4, frame.metadata.height/4)

        val bitmap = Bitmap.createBitmap(frame.metadata.width/16, frame.metadata.height/8, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(pixels)

        // Use the bitmap scaling to have neighbor pixels averaged, reducing overall noise
        return Bitmap.createScaledBitmap(bitmap, 8, 8, false)
    }

    private fun byteToArgbPixel(byte: Byte): Int {
        val v = byte.toInt() and 0xFF
        return 0xFF shl 24 or (v shl 16) or (v and 0xff shl 8) or (v and 0xff)
    }

    private fun probeFrame(frame: Frame): IntBuffer {
        val skipPixelsX = 16
        val skipLinesY = 7
        val width = frame.metadata.width
        val height = frame.metadata.height
        val grayscaleImageDate = frame.grayscaleImageData

        if (width <= height) {
            // Due to modulo operation on the row width: pointer % width === 0
            // Todo: fix this, or check API on orientation
            throw java.lang.Exception("detection only works in landscape mode")
        }

        var pixelCount = height * width
        var pointer = 0

        var targetFrameSize = pixelCount / (skipPixelsX * (skipLinesY+1))
        var targetArray = IntArray(targetFrameSize)
        var targetArrayCursor = 0

        // Iterate all equidistant pixels (defined by skip values)
        while (pointer < pixelCount) {
            targetArray[targetArrayCursor] = byteToArgbPixel(grayscaleImageDate[pointer])

            // Skip pixels on the x axis...
            pointer += skipPixelsX
            // ... until the end of the row is reached
            if (pointer % width === 0) {
                // Jump `skipLinesY` lines ahead
                pointer += skipLinesY * frame.metadata.width
            }
            targetArrayCursor += 1
        }

        if (targetFrameSize != targetArrayCursor) {
            throw java.lang.Exception("Iterated pixel count does not add up with expected pixel count. Expected:" + targetFrameSize + ", got: " + targetArrayCursor)
        }

        return IntBuffer.wrap(targetArray)
    }

    override fun receiveFrame(frame: Frame?) {
        frameCounter += 1
        // Drop framerate to 7,5 fps to compare with JS implementation. The framerate is the time-basis for the filter
        if (frameCounter % 2 === 0) return

        val startTime = System.currentTimeMillis()

        // println("Did receive a frame")
        super.receiveFrame(frame)
        if (frame === null) return
        try {
            val bitmap = this.frameToBitmap(frame)
            val scaledFrame = Frame
                .Builder()
                .setBitmap(bitmap)
                .setRotation(frame.metadata.rotation)
                .build()

            val imgData = scaledFrame.grayscaleImageData.array().map { it.toInt() and 0xFF }.toIntArray()
            this.imageComparator.addImage(imgData)
            // println("Receive frame took" + (System.currentTimeMillis() - startTime) + "ms")
        } catch (e: java.lang.Exception) {
            println(e)
        }
    }

    override fun detect(frame: Frame?): SparseArray<Motion> {
        if (frame == null) {
            throw IllegalArgumentException("No frame supplied.")
        } else {
            try {
                val sparseArray = SparseArray<Motion>()
                val motion = Motion()
                try {
                    val motionDetected = continousMovementDetection.movementDetected()

                    if (motionDetected) {
                        motion.type = MOTION_DETECTED
                        //Timber.d("MOTION_DETECTED")
                    } else {
                        motion.type = MOTION_NOT_DETECTED
                        //Timber.d("MOTION_NOT_DETECTED")
                    }
                } catch (e: Exception) {
                    Timber.e(e.message)
                    motion.type = MOTION_NOT_DETECTED
                }
                sparseArray.put(0, motion)
                return sparseArray
            } catch (e: java.lang.Exception) {
                println(e)
                return SparseArray<Motion>()
            }
        }
    }

    class Builder(private val minLuma: Int, private val motionLeniency: Int) {
        fun build(): MotionDetector {
            return MotionDetector(minLuma, motionLeniency)
        }
    }
}

class ContinousMotionDetection(imageComparator: ImageCompare) {
    private var movementCounter = 0
    private val maxCounterValues = 8
    private val imageComparator = imageComparator

    private fun updateMovementCounter(changedSegmentCount: Int) {
        val movementDetected = changedSegmentCount >= 3 && changedSegmentCount < 30
        var counterDirection = if (movementDetected) 1 else -1

        // Prevent counter overflow
        this.movementCounter = Math.max((this.movementCounter + counterDirection) % maxCounterValues, 0)
    }

    fun movementDetected(): Boolean {
        val changedSegmentCount = this.imageComparator.segmentsOverTheThresholdCount()
        if (changedSegmentCount === null) {
            return false
        }

        this.updateMovementCounter(changedSegmentCount)
        // println(this.movementCounter)
        val organicMovementDetected = ((this.movementCounter-1) * changedSegmentCount) > 10
        if (organicMovementDetected) {
            return true
        }

        return false
    }
}

class Average(size: Int) {
    private var pointer = 0
    private var size = 0
    private var maxSize = size
    private var data = IntArray(size)

    fun push(value: Int) {
        this.pointer = (this.pointer + 1) % this.maxSize
        this.data[this.pointer] = value
        this.size = Math.min(this.size + 1, this.maxSize)
    }

    fun avg(): Double {
        var sum = 0
        for (i in 0..(this.size-1)) {
            val pointer = (this.pointer + i) % this.maxSize
            sum += this.data[pointer]
        }

        return 1.0 * sum / this.size
    }
}

class ImageCompare(pixelCount: Int) {
    private var previous: IntArray? = null
    private var current: IntArray? = null
    private val pixelCount: Int = pixelCount
    private var currentDeltaMatrix: IntArray? = null
    private val averageThresholdCollection = Average(10)

    fun addImage(img: IntArray) {
        this.previous = this.current
        this.current = img

        this.currentDeltaMatrix = this.calcDeltaValues()
        val threshold = this.currentThreshold()
        this.averageThresholdCollection.push(threshold)
    }

    fun averageThreshold(): Double {
        return this.averageThresholdCollection.avg() * 1.5
    }

    fun calcDeltaValues(): IntArray? {
        val previous = this.previous ?: return null
        val current = this.current ?: return null

        var deltaMatrix = IntArray(this.pixelCount)
        for (i in 0..(this.pixelCount-1)) {
            deltaMatrix[i] = Math.abs(current[i] - previous[i])
        }

        return deltaMatrix
    }

    fun segmentsOverTheThresholdMap(): IntArray? {
        val avgThreshold: Double = this.averageThreshold()

        return this.currentDeltaMatrix?.map {
            if (it > avgThreshold) {
                1
            } else {
                0
            }
        }?.toIntArray()
    }

    fun segmentsOverTheThresholdCount(): Int? {
        return this.segmentsOverTheThresholdMap()?.filter { it == 1 }?.count()
    }

    fun currentThreshold(): Int {
        val maxChangedSegments = 4
        if (this.currentDeltaMatrix === null) return Int.MAX_VALUE

        val arr = this.currentDeltaMatrix!!
        arr.sort()
        arr.reverse()

        // Current threshold is the next item above the lowest allowed segments
        return arr[maxChangedSegments]
    }
}
