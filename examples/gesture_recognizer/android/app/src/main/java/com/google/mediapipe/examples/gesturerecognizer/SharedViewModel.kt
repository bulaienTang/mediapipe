package com.google.mediapipe.examples.gesturerecognizer

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedViewModel : ViewModel() {
    private val _imageLiveData = MutableLiveData<Bitmap>()
    val imageLiveData: LiveData<Bitmap> = _imageLiveData

    fun postImage(image: Bitmap) {
        _imageLiveData.postValue(image)
    }

    private val _gestureRecognitionResult = MutableLiveData<Pair<String, Float>>()
    val gestureRecognitionResult: LiveData<Pair<String, Float>> = _gestureRecognitionResult

    fun postGestureRecognitionResult(label: String, confidence: Float) {
        _gestureRecognitionResult.postValue(Pair(label, confidence))
    }
}