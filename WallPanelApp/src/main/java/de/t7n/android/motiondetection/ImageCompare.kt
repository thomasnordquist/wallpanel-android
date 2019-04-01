package de.t7n.android.motiondetection

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
