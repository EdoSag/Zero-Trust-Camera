package dev.sagron.zerotrustcamera.capture.ui.fragment

import androidx.recyclerview.widget.RecyclerView
import dev.sagron.zerotrustcamera.capture.databinding.GallerySlideBinding

class GallerySlide(val binding: GallerySlideBinding) : RecyclerView.ViewHolder(binding.root) {
    @Volatile var currentPostion = 0
}
