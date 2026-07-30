package com.pleaseinconvenienceme.pim

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory

class ReviewHelperImpl : ReviewHelper {
    override fun launchReview(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                manager.launchReviewFlow(activity, reviewInfo)
            }
        }
    }
}
