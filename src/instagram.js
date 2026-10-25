sbStartGuard({
  site: "instagram",
  htmlClass: "sb-ig",
  // Matches /reels/, /reels/ID/, /reel/ID/ and /<user>/reel/ID/.
  resolveTarget(url, action) {
    if (!/^\/(?:[\w.]+\/)?reels?(?:\/|$)/.test(url.pathname)) return null;
    // A profile's reels tab (/<user>/reels/) goes back to that profile in home mode.
    const profile = url.pathname.match(/^\/([\w.]+)\/reels\/?$/);
    if (action === "home") return profile ? `${url.origin}/${profile[1]}/` : `${url.origin}/`;
    return "BLOCK";
  }
});
