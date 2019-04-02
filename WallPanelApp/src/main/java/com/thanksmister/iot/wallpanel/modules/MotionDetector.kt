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

import android.graphics.Bitmap
import android.util.SparseArray

import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.Frame
import com.thanksmister.iot.wallpanel.modules.Motion.Companion.MOTION_DETECTED
import com.thanksmister.iot.wallpanel.modules.Motion.Companion.MOTION_NOT_DETECTED
import de.t7n.android.motiondetection.Average

import timber.log.Timber
import de.t7n.android.motiondetection.ContinousMotionDetection
import de.t7n.android.motiondetection.FrameProcessor
import de.t7n.android.motiondetection.ImageCompare
import java.nio.ByteBuffer
import java.nio.IntBuffer

/**
 * Created by Michael Ritchie on 7/6/18.
 */
class MotionDetector private constructor(private val minLuma: Int, private val motionLeniency: Int) : Detector<Motion>() {
    private val imageComparator = ImageCompare(64)
    private val continousMovementDetection = ContinousMotionDetection(this.imageComparator)
    private var frameCounter = 0
    private var frameAverage = Average(30)
    private var lastFrameReceived = System.currentTimeMillis()

    override fun receiveFrame(frame: Frame?) {
        val start = System.currentTimeMillis()

        super.receiveFrame(frame)

        // Drop framerate to 7,5 fps to compare with JS implementation. The framerate is the time-basis for the filter
        // @Todo: 
        frameCounter += 1
        frameAverage.push(System.currentTimeMillis() - this.lastFrameReceived)
        this.lastFrameReceived = System.currentTimeMillis()

        if (frameAverage.avg() < 140 && frameCounter % 2 == 0) return
        if (frame === null) return

        try {
            val bitmap = FrameProcessor.scaledBitmap(frame)

            val grayscaleData = IntBuffer.wrap(IntArray(64))
            bitmap.copyPixelsToBuffer(grayscaleData)

            // Extract a channel channel from the scaled image
            val imgData = grayscaleData.array().map { it and 0xFF }.toIntArray()

            this.imageComparator.addImage(imgData)
            println("Took " + (System.currentTimeMillis()-start)+"ms, averageInterval: " + frameAverage.avg() + "ms")
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

