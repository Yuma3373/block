const site = new URLSearchParams(location.search).get("site");

if (site === "instagram") {
  document.getElementById("title").textContent = "릴스는 차단되어 있어요";
  document.getElementById("home").href = "https://www.instagram.com/";
}

// The blocked URL was replaced in history, so going back skips it.
document.getElementById("back").addEventListener("click", () => {
  if (history.length > 1) history.back();
  else location.href = document.getElementById("home").href;
});
