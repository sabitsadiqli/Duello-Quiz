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
