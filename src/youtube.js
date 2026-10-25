// Labels YouTube leaves untranslated or uses for the Shorts entry points.
const SHORTS_LABELS = new Set(["shorts", "쇼츠"]);

sbStartGuard({
  site: "youtube",
  htmlClass: "sb-yt",
  resolveTarget(url, action) {
    const match = url.pathname.match(/^\/shorts(?:\/([\w-]+))?/);
    if (!match) return null;
    if (action === "watch" && match[1]) return `${url.origin}/watch?v=${match[1]}`;
    if (action === "home" || action === "watch") return `${url.origin}/`;
    return "BLOCK";
  },
  // Search filter chips and channel tabs carry no stable attribute, so match by text.
  onTick() {
    document
      .querySelectorAll("yt-chip-cloud-chip-renderer, yt-tab-shape, tp-yt-paper-tab, ytm-chip-cloud-chip-renderer")
      .forEach((el) => {
        if (SHORTS_LABELS.has(el.textContent.trim().toLowerCase())) el.classList.add("sb-hidden");
      });
  }
});
