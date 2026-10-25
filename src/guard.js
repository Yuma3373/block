// Generic watcher: toggles the hide class on <html> and sends the tab away
// whenever the URL lands on blocked content. Both sites are SPAs, so the URL
// is polled instead of relying on full page loads.
function sbStartGuard({ site, htmlClass, resolveTarget, onTick }) {
  let config = null;
  let lastHref = "";

  function blockedPageUrl() {
    const params = new URLSearchParams({ site, from: location.href });
    return chrome.runtime.getURL("blocked/blocked.html") + "?" + params;
  }

  function check() {
    if (!config) return;
    document.documentElement.classList.toggle(htmlClass, config.enabled);
    if (!config.enabled) return;

    if (location.href !== lastHref) {
      lastHref = location.href;
      const target = resolveTarget(new URL(location.href), config.action);
      if (target) {
        document.querySelectorAll("video").forEach((v) => v.pause());
        location.replace(target === "BLOCK" ? blockedPageUrl() : target);
        return;
      }
    }
    if (onTick && document.body) onTick();
  }

  sbLoadSettings().then((all) => {
    config = all[site];
    check();
  });

  chrome.storage.onChanged.addListener((changes, area) => {
    if (area !== "sync" || !changes[site]) return;
    config = { ...SB_DEFAULTS[site], ...changes[site].newValue };
    lastHref = "";
    check();
  });

  setInterval(check, 300);
  window.addEventListener("popstate", check);
  document.addEventListener("yt-navigate-start", check);
  document.addEventListener("yt-navigate-finish", check);
}
