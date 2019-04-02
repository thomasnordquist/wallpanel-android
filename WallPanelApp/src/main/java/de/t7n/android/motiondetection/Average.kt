package de.t7n.android.motiondetection

class Average(size: Int) {
    private var pointer: Int = 0
    private var size: Int = 0
    private var maxSize: Int = size
    private var data = LongArray(size)

    fun push(value: Long) {
        this.pointer = (this.pointer + 1) % this.maxSize
        this.data[this.pointer] = value
        this.size = Math.min(this.size + 1, this.maxSize)
    }

    fun avg(): Double {
        var sum: Long = 0
        for (i in 0..(this.size-1)) {
            val pointer = (this.pointer + i) % this.maxSize
            sum += this.data[pointer]
        }

        return 1.0 * sum / this.size
    }
}