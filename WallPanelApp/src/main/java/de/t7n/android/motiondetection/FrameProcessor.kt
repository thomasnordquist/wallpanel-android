package de.t7n.android.motiondetection

import android.graphics.Bitmap
import com.google.android.gms.vision.Frame
import java.nio.ByteBuffer

class FrameProcessor {
    companion object {
        fun frameToBitmap(frame: Frame): Bitmap {
            var source = frame.grayscaleImageData.array().map { it + 127 }.toIntArray()
            val pixelCount = frame.metadata.width * frame.metadata.height
            var dest = ByteArray(pixelCount)

            // Remove additional color data that may be at the end of the grayscale pixels
            System.arraycopy(source, 0, dest, 0, pixelCount)

            val bitmap = Bitmap.createBitmap(frame.metadata.width, frame.metadata.height, Bitmap.Config.ALPHA_8)
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(dest))

            // Use the bitmap scaling to have neighbor pixels averaged, reducing overall noise
            return Bitmap.createScaledBitmap(bitmap, 8, 8, false)
        }
    }
}
