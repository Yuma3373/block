// Shared between content scripts and the popup.
const SB_DEFAULTS = {
  youtube: { enabled: true, action: "block" }, // block | home | watch
  instagram: { enabled: true, action: "block" } // block | home
};

function sbLoadSettings() {
  return new Promise((resolve) => {
    chrome.storage.sync.get(SB_DEFAULTS, (stored) => {
      resolve({
        youtube: { ...SB_DEFAULTS.youtube, ...stored.youtube },
        instagram: { ...SB_DEFAULTS.instagram, ...stored.instagram }
      });
    });
  });
}
