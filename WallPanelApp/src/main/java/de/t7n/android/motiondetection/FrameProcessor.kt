package de.t7n.android.motiondetection

import android.graphics.Bitmap
import com.google.android.gms.vision.Frame
import java.nio.ByteBuffer
import java.nio.IntBuffer
import kotlin.math.roundToInt

interface ImageContainer {

}

class FrameProcessor {
    companion object {
        fun scaledBitmap(frame: Frame): Bitmap {
            val sampledBitmap = sampleDetectorFrame(frame, 2500)

            // Use the bitmap scaling to have neighbor pixels averaged, reducing overall noise
            return Bitmap.createScaledBitmap(sampledBitmap, 8, 8, false)
        }

        private fun sampleDetectorFrame(frame: Frame, samples: Int): Bitmap {
            val stepCount = Math.floor(Math.sqrt(samples * 1.0)).toInt()
            val stepsSizeX = (frame.metadata.width / stepCount)
            val stepsSizeY = (frame.metadata.height / stepCount)
            val frameData = frame.grayscaleImageData

            val targetImg = IntArray(stepCount * stepCount)
            var targetPointer = 0
            for (y in 0 until stepCount) {
                val rowPointer = (y * stepsSizeY * frame.metadata.width)
                for (x in 0 until stepCount) {
                    val pointer = rowPointer + (x * stepsSizeX)
                    val pixel = greyscaleToArgb(frameData[pointer])
                    targetImg[targetPointer] = pixel
                    targetPointer += 1
                }
            }

            val bitmap = Bitmap.createBitmap(stepCount, stepCount, Bitmap.Config.ALPHA_8)
            bitmap.setPixels(targetImg, 0, stepCount, 0, 0, stepCount, stepCount)
            // bitmap.copyPixelsFromBuffer(IntBuffer.wrap(targetImg))

            return bitmap
        }

        private fun greyscaleToArgb(it: Byte): Int {
            val intensity = it.toInt() and 0xFF
            val A = 255
            val R = intensity
            val G = intensity
            val B = intensity

            return (A and 0xff) shl 24 or (B and 0xff) shl 16 or (G and 0xff) shl 8 or (R and 0xff)
        }
    }
}
