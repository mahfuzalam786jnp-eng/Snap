package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.LicensedMusicCatalog
import com.example.data.model.VideoQualityOption
import com.example.ui.components.formatCompactCount
import com.example.ui.components.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app metadata and firebase database config are initialized`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        val dbId = context.getString(R.string.firestore_database_id)
        assertEquals("SNAP", appName)
        assertTrue(dbId.isNotBlank())
    }

    @Test
    fun `reels catalog and formatting utilities work as expected`() {
        assertTrue(LicensedMusicCatalog.tracks.isNotEmpty())
        assertEquals(VideoQualityOption.HD_720, VideoQualityOption.fromValue("720p"))
        assertEquals("1.5K", formatCompactCount(1500L))
        assertEquals("01:00", formatDuration(60L))
    }
}
