package com.rommie.app.data.repository

import com.rommie.app.model.ProofSubmission
import com.rommie.app.model.ProofType
import org.junit.Assert.*
import org.junit.Test

class ProofValidationTest {
    @Test fun validPhotoAndVideoLimits() {
        ProofValidation.validateUpload(ProofType.PHOTO, "image/jpeg", ProofValidation.PHOTO_LIMIT)
        ProofValidation.validateUpload(ProofType.PHOTO, "image/png", 1)
        ProofValidation.validateUpload(ProofType.VIDEO, "video/mp4", ProofValidation.VIDEO_LIMIT)
    }
    @Test fun rejectsEmptyOversizedAndMismatchedTypes() {
        for (size in listOf(0L, -1L, ProofValidation.PHOTO_LIMIT + 1)) {
            assertThrows(IllegalArgumentException::class.java) { ProofValidation.validateUpload(ProofType.PHOTO, "image/png", size) }
        }
        assertThrows(IllegalArgumentException::class.java) { ProofValidation.validateUpload(ProofType.VIDEO, "image/png", 1) }
        assertThrows(IllegalArgumentException::class.java) { ProofValidation.validateUpload(ProofType.PHOTO, "text/html", 1) }
    }
    @Test fun canonicalPathIncludesHouseholdTaskAndWorker() {
        assertEquals("households/home/completions/task/proof/worker/proof", ProofValidation.path("home", "task", "worker", "proof"))
    }
    @Test fun rejectsTraversalAndPublicUrls() {
        for (id in listOf("../proof", "", "a/b", "x".repeat(129))) {
            assertThrows(IllegalArgumentException::class.java) { ProofValidation.path("home", "task", "worker", id) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProofValidation.validate("home", "task", "worker", ProofSubmission("proof", "https://example.com/photo", ProofType.PHOTO))
        }
    }
    @Test fun rejectsForeignWorkerOrHousehold() {
        val proof = ProofSubmission("proof", ProofValidation.path("home", "task", "worker", "proof"), ProofType.PHOTO)
        assertThrows(IllegalArgumentException::class.java) { ProofValidation.validate("other", "task", "worker", proof) }
        assertThrows(IllegalArgumentException::class.java) { ProofValidation.validate("home", "task", "other", proof) }
    }
}
