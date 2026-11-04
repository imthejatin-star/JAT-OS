"use strict";

let installedApps = [];
let toastTimer = null;

const $ = id => document.getElementById(id);

function notify(message) {
    const el = $("toast");
    el.textContent = message;
    el.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove("show"), 2200);
}

function deviceSettings() {
    if (window.JAT && JAT.openDeviceSettings) JAT.openDeviceSettings();
    else notify("Device settings are available in the installed Android app.");
}

function showScreen(id) {
    document.querySelectorAll(".screen").forEach(s => s.classList.remove("active"));
    const target = $(id);
    if (target) target.classList.add("active");

    document.querySelectorAll(".nav-item").forEach(n => {
        n.classList.toggle("active", n.dataset.screen === id);
    });

    if (id === "applicationsScreen") renderApps();
    if (id === "homeScreen") renderFavourites();
    window.scrollTo(0, 0);
}

function goBackScreen() {
    const active = document.querySelector(".screen.active");
    if (active && active.id !== "homeScreen") showScreen("homeScreen");
}

function loadApps() {
    if (!window.JAT || typeof JAT.getApps !== "function") {
        installedApps = [];
        updateCounts();
        renderApps();
        notify("Open the installed JAT OS Android app to load device applications.");
        return;
    }

    try {
        const data = JAT.getApps();
        const parsed = JSON.parse(data || "[]");
        installedApps = Array.isArray(parsed) ? parsed.filter(a =>
            a && a.name && a.package && a.activity
        ) : [];
        updateCounts();
        renderApps();
        renderFavourites();
    } catch (error) {
        installedApps = [];
        updateCounts();
        renderApps();
        notify("Could not read the installed applications list.");
    }
}

function refreshApps() {
    loadApps();
    notify("Application list refreshed");
}

function updateCounts() {
    $("appCount").textContent = installedApps.length + " APPLICATIONS DETECTED";
    $("appTotal").textContent = installedApps.length + " APPLICATIONS";
    if (window.JAT && JAT.getDeviceName) {
        $("deviceName").textContent = String(JAT.getDeviceName()).toUpperCase();
    }
}

function getSortedApps(list) {
    const order = $("sortOrder")?.value || "az";
    return [...list].sort((a, b) => {
        const result = a.name.localeCompare(b.name);
        return order === "za" ? -result : result;
    });
}

function makeAppCard(app, index) {
    const card = document.createElement("button");
    card.className = "app-card";
    card.type = "button";
    card.setAttribute("aria-label", "Open " + app.name);

    if (app.icon && app.icon.startsWith("data:image/")) {
        const img = document.createElement("img");
        img.className = "app-icon";
        img.alt = "";
        img.src = app.icon;
        img.onerror = () => {
            const fallback = document.createElement("div");
            fallback.className = "app-fallback";
            fallback.textContent = (app.name || "?").slice(0, 1).toUpperCase();
            img.replaceWith(fallback);
        };
        card.appendChild(img);
    } else {
        const fallback = document.createElement("div");
        fallback.className = "app-fallback";
        fallback.textContent = (app.name || "?").slice(0, 1).toUpperCase();
        card.appendChild(fallback);
    }

    const name = document.createElement("span");
    name.className = "app-name";
    name.textContent = app.name;
    card.appendChild(name);

    card.addEventListener("click", () => {
        if (window.JAT && JAT.launchApp) {
            const success = JAT.launchApp(app.package, app.activity);
            if (success === false) notify("Unable to open " + app.name);
        } else {
            notify("App launching requires the installed Android version.");
        }
    });

    return card;
}

function renderApps() {
    const container = $("allApps");
    if (!container) return;

    container.replaceChildren();
    const query = ($("appSearch")?.value || "").trim().toLowerCase();
    const filtered = getSortedApps(installedApps.filter(app =>
        app.name.toLowerCase().includes(query) ||
        app.package.toLowerCase().includes(query)
    ));

    filtered.forEach((app, index) => container.appendChild(makeAppCard(app, index)));
    $("emptyApps").hidden = filtered.length !== 0;
    $("appTotal").textContent = filtered.length + " OF " + installedApps.length + " APPLICATIONS";
}

function renderFavourites() {
    const container = $("favourites");
    if (!container) return;

    container.replaceChildren();
    const saved = JSON.parse(localStorage.getItem("jat_favourites") || "[]");
    let favourites = saved.map(pkg => installedApps.find(a => a.package === pkg)).filter(Boolean);

    if (!favourites.length) favourites = installedApps.slice(0, 4);
    favourites.slice(0, 8).forEach((app, index) => {
        container.appendChild(makeAppCard(app, index));
    });
}

function clearSearch() {
    $("appSearch").value = "";
    renderApps();
}

function saveAppearance() {
    const settings = {
        columns: $("gridColumns").value,
        iconSize: $("iconSize").value,
        labels: $("showLabels").checked,
        sort: $("sortOrder").value,
        theme: $("themeMode").value,
        animations: $("animations").checked
    };
    localStorage.setItem("jat_appearance", JSON.stringify(settings));
    applyAppearance();
}

function applyAppearance() {
    let settings = {};
    try { settings = JSON.parse(localStorage.getItem("jat_appearance") || "{}"); } catch (_) {}

    $("gridColumns").value = settings.columns || "4";
    $("iconSize").value = settings.iconSize || "medium";
    $("showLabels").checked = settings.labels !== false;
    $("sortOrder").value = settings.sort || "az";
    $("themeMode").value = settings.theme || "dark";
    $("animations").checked = settings.animations !== false;

    document.documentElement.style.setProperty("--columns", settings.columns || "4");
    document.body.classList.toggle("hide-labels", settings.labels === false);
    document.body.classList.toggle("midnight", settings.theme === "midnight");
    document.body.classList.remove("size-small", "size-medium", "size-large");
    document.body.classList.add("size-" + (settings.iconSize || "medium"));
    $("matrix").style.display = settings.animations === false ? "none" : "block";
    renderApps();
    renderFavourites();
}

function resetAppearance() {
    localStorage.removeItem("jat_appearance");
    applyAppearance();
    notify("Appearance reset");
}

function saveNotes() {
    localStorage.setItem("jat_notes", $("notesArea").value);
    $("noteStatus").textContent = "SAVED LOCALLY";
}

function updateClock() {
    const now = new Date();
    $("clock").textContent = now.toLocaleTimeString([], {
        hour: "2-digit", minute: "2-digit"
    });
}

function initMatrix() {
    const canvas = $("matrix");
    const ctx = canvas.getContext("2d");
    let width, height, drops, fontSize = 14;
    const chars = "01アイウエオカキクケコ";

    function resize() {
        width = canvas.width = window.innerWidth;
        height = canvas.height = window.innerHeight;
        drops = Array(Math.ceil(width / fontSize)).fill(1);
    }

    function draw() {
        if (canvas.style.display === "none") return;
        ctx.fillStyle = "rgba(5,8,12,0.09)";
        ctx.fillRect(0, 0, width, height);
        ctx.fillStyle = "#36b98e";
        ctx.font = fontSize + "px monospace";

        for (let i = 0; i < drops.length; i++) {
            const char = chars[Math.floor(Math.random() * chars.length)];
            ctx.fillText(char, i * fontSize, drops[i] * fontSize);
            if (drops[i] * fontSize > height && Math.random() > .98) drops[i] = 0;
            drops[i]++;
        }
    }

    resize();
    window.addEventListener("resize", resize);
    setInterval(draw, 90);
}

document.addEventListener("DOMContentLoaded", () => {
    $("appSearch").addEventListener("input", renderApps);
    $("notesArea").value = localStorage.getItem("jat_notes") || "";
    $("notesArea").addEventListener("input", saveNotes);

    applyAppearance();
    loadApps();
    updateClock();
    setInterval(updateClock, 15000);
    initMatrix();
});
