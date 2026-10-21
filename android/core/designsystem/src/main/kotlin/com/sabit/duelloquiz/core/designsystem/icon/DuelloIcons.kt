package com.sabit.duelloquiz.core.designsystem.icon

import androidx.annotation.DrawableRes
import com.sabit.duelloquiz.core.designsystem.R

/** Bundled Material Symbols Rounded (Apache 2.0) as vector drawables, plus the Google "G". */
object DuelloIcons {
    @DrawableRes val AccountCircle = R.drawable.ic_account_circle
    @DrawableRes val ArrowBack = R.drawable.ic_arrow_back
    @DrawableRes val BrokenImage = R.drawable.ic_broken_image
    @DrawableRes val Celebration = R.drawable.ic_celebration
    @DrawableRes val Check = R.drawable.ic_check
    @DrawableRes val ChevronRight = R.drawable.ic_chevron_right
    @DrawableRes val Close = R.drawable.ic_close
    @DrawableRes val CloudOff = R.drawable.ic_cloud_off
    @DrawableRes val CloudUpload = R.drawable.ic_cloud_upload
    @DrawableRes val Delete = R.drawable.ic_delete
    @DrawableRes val Error = R.drawable.ic_error
    @DrawableRes val FactCheck = R.drawable.ic_fact_check
    @DrawableRes val Inbox = R.drawable.ic_inbox
    @DrawableRes val Info = R.drawable.ic_info
    @DrawableRes val Language = R.drawable.ic_language
    @DrawableRes val Logout = R.drawable.ic_logout
    @DrawableRes val Mail = R.drawable.ic_mail
    @DrawableRes val OpenInNew = R.drawable.ic_open_in_new
    @DrawableRes val Policy = R.drawable.ic_policy
    @DrawableRes val Settings = R.drawable.ic_settings
    @DrawableRes val Timer = R.drawable.ic_timer
    @DrawableRes val TimerOff = R.drawable.ic_timer_off
    @DrawableRes val Visibility = R.drawable.ic_visibility
    @DrawableRes val VisibilityOff = R.drawable.ic_visibility_off
    @DrawableRes val Warning = R.drawable.ic_warning

    // Duel and profile (components.md §11–§23).
    @DrawableRes val Block = R.drawable.ic_block
    @DrawableRes val Bolt = R.drawable.ic_bolt
    @DrawableRes val CheckCircle = R.drawable.ic_check_circle
    @DrawableRes val Devices = R.drawable.ic_devices
    @DrawableRes val DoubleArrowUp = R.drawable.ic_keyboard_double_arrow_up
    @DrawableRes val DragHandle = R.drawable.ic_drag_handle
    @DrawableRes val Edit = R.drawable.ic_edit
    @DrawableRes val EmojiEvents = R.drawable.ic_emoji_events
    @DrawableRes val Flag = R.drawable.ic_flag
    @DrawableRes val Handshake = R.drawable.ic_handshake
    @DrawableRes val HourglassEmpty = R.drawable.ic_hourglass_empty
    @DrawableRes val Lightbulb = R.drawable.ic_lightbulb
    @DrawableRes val Lock = R.drawable.ic_lock
    @DrawableRes val PauseCircle = R.drawable.ic_pause_circle
    @DrawableRes val Person = R.drawable.ic_person
    @DrawableRes val PersonOff = R.drawable.ic_person_off
    @DrawableRes val PersonSearch = R.drawable.ic_person_search
    @DrawableRes val SystemUpdate = R.drawable.ic_system_update
    @DrawableRes val TrendingDown = R.drawable.ic_trending_down
    @DrawableRes val TrendingFlat = R.drawable.ic_trending_flat
    @DrawableRes val TrendingUp = R.drawable.ic_trending_up
    @DrawableRes val Wifi = R.drawable.ic_wifi

    // v2 (components.md §6, quiz-result.md): duel hero card, review entry card.
    @DrawableRes val Swords = R.drawable.ic_swords
    @DrawableRes val Rule = R.drawable.ic_rule

    /**
     * Category icon by the server `iconKey` (OQ-044, components.md §5). Unknown keys get the
     * generic `category` icon, so a new server category never needs an app update (HOME-01).
     */
    @DrawableRes
    fun category(iconKey: String): Int = when (iconKey) {
        "geography" -> R.drawable.ic_public
        "history" -> R.drawable.ic_history_edu
        "biology" -> R.drawable.ic_eco
        "physics" -> R.drawable.ic_science
        "sports" -> R.drawable.ic_sports_soccer
        else -> R.drawable.ic_category
    }

    /** Multicolour; never tint (Google branding). */
    @DrawableRes val GoogleLogo = R.drawable.ic_google_logo
}
