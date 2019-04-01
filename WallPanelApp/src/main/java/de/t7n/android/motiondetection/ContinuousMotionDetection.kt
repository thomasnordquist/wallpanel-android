package de.t7n.android.motiondetection

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
        val organicMovementDetected = ((this.movementCounter-1) * changedSegmentCount) > 10
        if (organicMovementDetected) {
            return true
        }

        return false
    }
}