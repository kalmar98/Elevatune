package com.example.elevatune

interface AnalyzeProgressListener {
    fun onProgress(percent: Int)
    fun onComplete(pitchList: List<PitchPoint>)
    fun onError(e: Exception)
}