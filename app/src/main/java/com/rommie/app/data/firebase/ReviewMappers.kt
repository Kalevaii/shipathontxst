package com.rommie.app.data.firebase

import com.google.firebase.Timestamp
import com.rommie.app.model.Review

/** Private wire mapper. Never attach raw review records to household/completion state. */
internal object ReviewMappers {
    fun fromDocument(reviewerUid: String, householdId: String, completionId: String, fields: Map<String, Any?>): Review {
        require(fields["householdId"] == householdId && fields["completionId"] == completionId && fields["reviewerId"] == reviewerUid)
        val rating = fields["rating"] as? Long ?: error("Review rating must be an integer")
        require(rating in 1..10)
        val time = fields["submittedAt"] as? Timestamp ?: error("Review requires committed server time")
        return Review(reviewerUid, completionId, reviewerUid, rating.toInt(), time.toDate().time)
    }
}
