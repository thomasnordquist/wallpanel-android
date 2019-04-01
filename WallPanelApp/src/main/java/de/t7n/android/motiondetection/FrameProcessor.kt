package de.t7n.android.motiondetection

import android.graphics.Bitmap
import com.google.android.gms.vision.Frame
import java.nio.IntBuffer

class FrameProcessor {
    companion object {
        fun frameToBitmap(frame: Frame): Bitmap {
            val pixels = frameToSmallerArgbImage(frame)
            val bitmap = Bitmap.createBitmap(frame.metadata.width/16, frame.metadata.height/8, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(pixels)

            // Use the bitmap scaling to have neighbor pixels averaged, reducing overall noise
            return Bitmap.createScaledBitmap(bitmap, 8, 8, false)
        }

        /**
         * Probing equidistant points of the image to reduce computation time.
         *
         * @return IntBuffer ordered subset of pixels
         */
        private fun frameToSmallerArgbImage(frame: Frame): IntBuffer {
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

        private fun byteToArgbPixel(byte: Byte): Int {
            val v = byte.toInt() and 0xFF
            return 0xFF shl 24 or (v shl 16) or (v and 0xff shl 8) or (v and 0xff)
        }
    }
}
