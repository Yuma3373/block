package com.shortsblocker

// View IDs and labels come from the apps themselves and can change with
// updates; add new ones here when they do.
enum class Target(
    val packageName: String,
    // Full-screen short-form player.
    val playerViewIds: List<String>,
    // Bottom navigation tab that opens the player.
    val tabViewIds: List<String>,
    val tabLabels: List<String>,
    private val lightBackground: Int,
    private val darkBackground: Int,
    val noticeRes: Int,
) {
    YOUTUBE(
        packageName = "com.google.android.youtube",
        playerViewIds = listOf("reel_recycler", "reel_player_page_container", "reel_watch_player", "reel_watch_fragment_root"),
        tabViewIds = emptyList(),
        tabLabels = listOf("Shorts", "쇼츠"),
        lightBackground = 0xFFFFFFFF.toInt(),
        darkBackground = 0xFF0F0F0F.toInt(),
        noticeRes = R.string.notice_youtube,
    ),
    INSTAGRAM(
        packageName = "com.instagram.android",
        playerViewIds = listOf("clips_viewer_view_pager", "clips_viewer_container", "clips_video_container"),
        tabViewIds = listOf("clips_tab"),
        tabLabels = listOf("Reels", "릴스"),
        lightBackground = 0xFFFFFFFF.toInt(),
        darkBackground = 0xFF000000.toInt(),
        noticeRes = R.string.notice_instagram,
    );

    // Used to cover the tab when the real background colour could not be sampled.
    fun fallbackBackground(night: Boolean) = if (night) darkBackground else lightBackground

    companion object {
        fun forPackage(packageName: String): Target? = entries.firstOrNull { it.packageName == packageName }
    }
}
