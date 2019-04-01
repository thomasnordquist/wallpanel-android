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

import timber.log.Timber
import de.t7n.android.motiondetection.ContinousMotionDetection
import de.t7n.android.motiondetection.FrameProcessor
import de.t7n.android.motiondetection.ImageCompare

/**
 * Created by Michael Ritchie on 7/6/18.
 */
class MotionDetector private constructor(private val minLuma: Int, private val motionLeniency: Int) : Detector<Motion>() {
    private val imageComparator = ImageCompare(64)
    private val continousMovementDetection = ContinousMotionDetection(this.imageComparator)
    private var frameCounter = 0

    override fun receiveFrame(frame: Frame?) {
        super.receiveFrame(frame)

        // Drop framerate to 7,5 fps to compare with JS implementation. The framerate is the time-basis for the filter
        // @Todo: 
        frameCounter += 1

        if (frameCounter % 2 === 0) return
        if (frame === null) return

        try {
            val bitmap = FrameProcessor.frameToBitmap(frame)
            val scaledFrame = Frame
                .Builder()
                .setBitmap(bitmap)
                .setRotation(frame.metadata.rotation)
                .build()

            val imgData = scaledFrame.grayscaleImageData.array().map { it.toInt() and 0xFF }.toIntArray()
            this.imageComparator.addImage(imgData)
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

