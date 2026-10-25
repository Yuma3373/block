const SITES = ["youtube", "instagram"];

sbLoadSettings().then((settings) => {
  for (const site of SITES) {
    const enabled = document.getElementById(`${site}-enabled`);
    const action = document.getElementById(`${site}-action`);
    enabled.checked = settings[site].enabled;
    action.value = settings[site].action;
    action.disabled = !enabled.checked;

    const save = () => {
      action.disabled = !enabled.checked;
      chrome.storage.sync.set({ [site]: { enabled: enabled.checked, action: action.value } });
    };
    enabled.addEventListener("change", save);
    action.addEventListener("change", save);
  }
});
