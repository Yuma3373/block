package com.shortsblocker

// View IDs of each app's full-screen short-form player. These come from the
// apps themselves and can change with updates; add new ones here when they do.
enum class Target(val packageName: String, val playerViewIds: List<String>, val toastRes: Int) {
    YOUTUBE(
        "com.google.android.youtube",
        listOf("reel_recycler", "reel_player_page_container", "reel_watch_player", "reel_watch_fragment_root"),
        R.string.toast_youtube,
    ),
    INSTAGRAM(
        "com.instagram.android",
        listOf("clips_viewer_view_pager", "clips_viewer_container", "clips_video_container"),
        R.string.toast_instagram,
    );

    companion object {
        fun forPackage(packageName: String): Target? = entries.firstOrNull { it.packageName == packageName }
    }
}
