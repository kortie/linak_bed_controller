package dev.linakbed.smartthings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class NotificationTextExtractorTest {
    @Test
    fun `title, text, big text and lines are extracted in order without blanks or repeats`() {
        val sbn =
            statusBarNotification(
                title = "SmartThings",
                text = "아침 루틴",
                bigText = "아침 루틴",
                lines = arrayOf("#BED preset 2", " "),
            )

        val incoming = NotificationTextExtractor.extract(sbn)

        assertThat(incoming.texts).containsExactly("SmartThings", "아침 루틴", "#BED preset 2").inOrder()
        assertThat(incoming.packageName).isEqualTo("com.samsung.android.oneconnect")
        assertThat(incoming.key).isEqualTo(sbn.key)
        assertThat(incoming.postedAt).isEqualTo(Instant.ofEpochMilli(10_000))
        assertThat(incoming.sentAt).isEqualTo(Instant.ofEpochMilli(1_000))
    }

    @Test
    fun `missing fields are skipped`() {
        val incoming = NotificationTextExtractor.extract(statusBarNotification(title = null, text = "#BED stop"))

        assertThat(incoming.texts).containsExactly("#BED stop")
    }

    @Test
    fun `when of zero means the sent time is unknown`() {
        assertThat(NotificationTextExtractor.extract(statusBarNotification(whenMillis = 0)).sentAt).isNull()
    }

    @Test
    fun `group summaries are recognised`() {
        assertThat(NotificationTextExtractor.isGroupSummary(statusBarNotification(groupSummary = true))).isTrue()
        assertThat(NotificationTextExtractor.isGroupSummary(statusBarNotification())).isFalse()
    }
}
