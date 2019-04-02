package de.t7n.android.motiondetection

import android.graphics.Bitmap
import com.google.android.gms.vision.Frame
import java.nio.ByteBuffer
import java.nio.IntBuffer

class FrameProcessor {
    companion object {
        fun frameToBitmap(frame: Frame): Bitmap {
            var arr = frame.grayscaleImageData.array()
            var dest = ByteArray(640*480)
            System.arraycopy(arr, 0, dest, 0, 640*480)

            val bitmap = Bitmap.createBitmap(frame.metadata.width, frame.metadata.height, Bitmap.Config.ALPHA_8)
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(dest))

            // Use the bitmap scaling to have neighbor pixels averaged, reducing overall noise
            return Bitmap.createScaledBitmap(bitmap, 8, 8, false)
        }
    }
}
