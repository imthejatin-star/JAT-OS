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
    document.body.classList.toggle("eye-comfort", settings.theme === "eye");
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


/* USER-CREATED PRIVATE APP DIRECTORIES */
function getFolders() {
    try {
        const data = JSON.parse(localStorage.getItem("jat_user_folders") || "[]");
        return Array.isArray(data) ? data : [];
    } catch (_) {
        return [];
    }
}

function saveFolders(folders) {
    localStorage.setItem("jat_user_folders", JSON.stringify(folders));
    renderHomeFolders();
    renderManagedFolders();
    updateFolderSelector();
}

function createFolder() {
    const input = document.getElementById("newFolderName");
    const name = (input.value || "").trim();

    if (!name) {
        notify("Enter a folder name first");
        return;
    }

    const folders = getFolders();

    if (folders.some(f => f.name.toLowerCase() === name.toLowerCase())) {
        notify("A folder with this name already exists");
        return;
    }

    folders.push({
        id: "folder_" + Date.now() + "_" + Math.random().toString(36).slice(2, 7),
        name: name,
        apps: []
    });

    input.value = "";
    saveFolders(folders);
    updateFolderSelector();
    document.getElementById("folderTarget").value = folders[folders.length - 1].id;
    renderFolderAssignments();
    notify("Private directory created");
}

function updateFolderSelector() {
    const select = document.getElementById("folderTarget");
    if (!select) return;

    const previous = select.value;
    select.replaceChildren();

    const placeholder = document.createElement("option");
    placeholder.value = "";
    placeholder.textContent = "Choose a folder";
    select.appendChild(placeholder);

    getFolders().forEach(folder => {
        const option = document.createElement("option");
        option.value = folder.id;
        option.textContent = folder.name;
        select.appendChild(option);
    });

    if (getFolders().some(f => f.id === previous)) {
        select.value = previous;
    }
}

function renderFolderAssignments() {
    const container = document.getElementById("folderAppAssignments");
    const target = document.getElementById("folderTarget").value;
    const folder = getFolders().find(f => f.id === target);

    container.replaceChildren();

    if (!folder) {
        container.innerHTML = '<div class="assignment-empty">Select a directory to organize applications.</div>';
        return;
    }

    if (!installedApps.length) {
        container.innerHTML = '<div class="assignment-empty">No device applications loaded yet.</div>';
        return;
    }

    const assignedElsewhere = new Map();
    getFolders().forEach(f => {
        if (f.id !== folder.id) {
            (f.apps || []).forEach(pkg => assignedElsewhere.set(pkg, f.name));
        }
    });

    getSortedApps(installedApps).forEach(app => {
        const row = document.createElement("label");
        row.className = "assignment-item";

        const checkbox = document.createElement("input");
        checkbox.type = "checkbox";
        checkbox.value = app.package;
        checkbox.checked = (folder.apps || []).includes(app.package);

        const elsewhere = assignedElsewhere.get(app.package);
        if (elsewhere) {
            checkbox.checked = false;
            checkbox.disabled = true;
        }

        if (app.icon) {
            const img = document.createElement("img");
            img.src = app.icon;
            img.alt = "";
            row.appendChild(img);
        }

        const label = document.createElement("span");
        label.textContent = elsewhere
            ? app.name + " · in " + elsewhere
            : app.name;

        row.appendChild(label);
        row.appendChild(checkbox);
        container.appendChild(row);
    });
}

function saveFolderAssignments() {
    const target = document.getElementById("folderTarget").value;
    const folders = getFolders();
    const folder = folders.find(f => f.id === target);

    if (!folder) {
        notify("Select a directory first");
        return;
    }

    folder.apps = Array.from(
        document.querySelectorAll("#folderAppAssignments input[type=checkbox]:checked")
    ).map(input => input.value);

    saveFolders(folders);
    updateFolderSelector();
    document.getElementById("folderTarget").value = target;
    renderFolderAssignments();
    notify("Directory contents saved");
}

function renderHomeFolders() {
    const container = document.getElementById("homeFolders");
    if (!container) return;

    container.replaceChildren();

    getFolders().forEach(folder => {
        const count = (folder.apps || []).length;
        const card = document.createElement("button");
        card.type = "button";
        card.className = "folder-card";

        const top = document.createElement("div");
        top.className = "folder-card-top";

        const icon = document.createElement("span");
        icon.className = "folder-card-icon";
        icon.textContent = "▣";

        const number = document.createElement("span");
        number.className = "folder-card-count";
        number.textContent = String(count).padStart(2, "0") + " FILES";

        top.append(icon, number);

        const name = document.createElement("span");
        name.className = "folder-card-name";
        name.textContent = folder.name;

        const path = document.createElement("span");
        path.className = "folder-card-path";
        path.textContent = "ROOT / PRIVATE";

        card.append(top, name, path);
        card.addEventListener("click", () => openFolder(folder.id));
        container.appendChild(card);
    });

    if (!getFolders().length) {
        const empty = document.createElement("div");
        empty.className = "assignment-empty";
        empty.textContent = "No folders yet. Create your first directory in Customize.";
        container.appendChild(empty);
    }
}

function renderManagedFolders() {
    const container = document.getElementById("managedFolders");
    if (!container) return;

    container.replaceChildren();

    getFolders().forEach(folder => {
        const row = document.createElement("div");
        row.className = "managed-folder-row";

        const name = document.createElement("span");
        name.textContent = "▣  " + folder.name;

        const count = document.createElement("small");
        count.textContent = (folder.apps || []).length + " APPS";

        const remove = document.createElement("button");
        remove.textContent = "DELETE";
        remove.addEventListener("click", () => deleteFolder(folder.id));

        row.append(name, count, remove);
        container.appendChild(row);
    });

    if (!getFolders().length) {
        const empty = document.createElement("div");
        empty.className = "assignment-empty";
        empty.textContent = "No custom directories created.";
        container.appendChild(empty);
    }
}

function deleteFolder(id) {
    const folders = getFolders();
    const folder = folders.find(f => f.id === id);
    if (!folder) return;

    if (!confirm("Delete the folder '" + folder.name + "'? The apps themselves will not be deleted.")) {
        return;
    }

    saveFolders(folders.filter(f => f.id !== id));
    updateFolderSelector();
    renderFolderAssignments();
    notify("Directory deleted");
}

function openFolder(id) {
    const folder = getFolders().find(f => f.id === id);
    if (!folder) return;

    document.getElementById("folderModalTitle").textContent = folder.name.toUpperCase();
    document.getElementById("folderPathName").textContent = folder.name.toUpperCase();

    const container = document.getElementById("folderModalApps");
    container.replaceChildren();

    const apps = (folder.apps || [])
        .map(pkg => installedApps.find(app => app.package === pkg))
        .filter(Boolean);

    apps.forEach((app, index) => {
        container.appendChild(makeAppCard(app, index));
    });

    document.getElementById("folderModalEmpty").hidden = apps.length !== 0;
    document.getElementById("folderModal").classList.add("open");
    document.body.style.overflow = "hidden";
}

function closeFolder() {
    document.getElementById("folderModal").classList.remove("open");
    document.body.style.overflow = "";
}

document.addEventListener("DOMContentLoaded", () => {
    updateFolderSelector();
    renderHomeFolders();
    renderManagedFolders();

    const createInput = document.getElementById("newFolderName");
    if (createInput) {
        createInput.addEventListener("keydown", event => {
            if (event.key === "Enter") createFolder();
        });
    }

    document.addEventListener("keydown", event => {
        if (event.key === "Escape") closeFolder();
    });
});


/* JAT OS HACKER REFINEMENT */
(() => {
  const FAV_KEY = "jat_os_favourite_apps_v1";
  const readFavs = () => {
    try {
      const v = JSON.parse(localStorage.getItem(FAV_KEY) || "[]");
      return Array.isArray(v) ? v : [];
    } catch (_) { return []; }
  };
  const writeFavs = v => localStorage.setItem(FAV_KEY, JSON.stringify(v));

  function appIdentity(card) {
    const packageName =
      card.dataset.package ||
      card.dataset.packagename ||
      card.dataset.appPackage ||
      card.getAttribute("data-app-package") || "";

    const launchText = card.getAttribute("onclick") || "";
    const match = launchText.match(/launchApp\s*\(\s*['"]([^'"]+)/);
    const pkg = packageName || (match ? match[1] : "");

    const nameNode = card.querySelector(
      ".app-name, .app-title, .app-label, .name, h3, h4, p"
    );
    const name = (nameNode?.textContent || card.innerText || "")
      .trim().split("\n")[0].slice(0, 60);

    return { id: pkg || name, name, pkg };
  }

  function appCards() {
    const selectors = [
      ".app-card", ".app-item", ".application-card",
      "[data-package]", "[data-app-package]", "[data-packagename]"
    ];
    const found = new Set();
    selectors.forEach(s => document.querySelectorAll(s).forEach(el => {
      if (el.closest(".jat-favourites")) return;
      if (el.querySelector("img") || el.dataset.package ||
          el.dataset.packagename || el.dataset.appPackage) found.add(el);
    }));
    return [...found].filter(el => appIdentity(el).id);
  }

  function getFavouriteHost() {
    let host = document.querySelector("#jat-favourites");
    if (host) return host;

    host = document.createElement("section");
    host.id = "jat-favourites";
    host.className = "jat-favourites";
    host.innerHTML =
      '<div class="jat-favourites-head"><span>★ PINNED APPLICATIONS</span><span>QUICK ACCESS</span></div>' +
      '<div class="jat-favourites-grid" id="jat-favourites-grid"></div>';

    const main = document.querySelector(
      "main, #main-system, .main-content, .dashboard, #home"
    ) || document.body;

    const firstSection = main.querySelector(
      ".app-grid, #app-grid, .apps-grid, .app-drawer, .home-section"
    );
    if (firstSection && firstSection.parentNode) {
      firstSection.parentNode.insertBefore(host, firstSection);
    } else {
      main.insertBefore(host, main.firstChild);
    }
    return host;
  }

  function renderFavourites() {
    const host = getFavouriteHost();
    const grid = host.querySelector("#jat-favourites-grid");
    if (!grid) return;

    const favs = readFavs();
    const cards = appCards();
    const byId = new Map(cards.map(card => [appIdentity(card).id, card]));

    grid.replaceChildren();

    favs.forEach(id => {
      const original = byId.get(id);
      if (!original) return;

      const info = appIdentity(original);
      const tile = document.createElement("button");
      tile.type = "button";
      tile.className = "jat-favourite-tile";

      const image = original.querySelector("img");
      if (image) {
        const icon = document.createElement("img");
        icon.src = image.src;
        icon.alt = "";
        tile.appendChild(icon);
      }

      const label = document.createElement("span");
      label.textContent = info.name || "APPLICATION";
      tile.appendChild(label);

      tile.addEventListener("click", () => {
        const launch = original.querySelector(
          "button:not(.jat-favourite-star), [role='button']"
        );
        if (launch) launch.click();
        else original.click();
      });
      grid.appendChild(tile);
    });

    if (!favs.length) {
      const empty = document.createElement("div");
      empty.textContent = "NO PINNED APPS // SELECT ★ IN APP DRAWER";
      empty.style.cssText = "font:10px monospace;color:#66816d;padding:8px 2px";
      grid.appendChild(empty);
    }
  }

  function addFavouriteControls() {
    appCards().forEach(card => {
      if (card.querySelector(".jat-favourite-star")) return;

      const info = appIdentity(card);
      if (!info.id) return;

      card.classList.add("jat-favourite-anchor");

      const star = document.createElement("button");
      star.type = "button";
      star.className = "jat-favourite-star";
      star.setAttribute("aria-label", "Toggle favourite");
      star.textContent = readFavs().includes(info.id) ? "★" : "☆";
      star.classList.toggle("active", readFavs().includes(info.id));

      star.addEventListener("click", event => {
        event.preventDefault();
        event.stopPropagation();
        event.stopImmediatePropagation();

        const favs = readFavs();
        const index = favs.indexOf(info.id);
        if (index >= 0) favs.splice(index, 1);
        else favs.push(info.id);

        writeFavs(favs);
        star.textContent = favs.includes(info.id) ? "★" : "☆";
        star.classList.toggle("active", favs.includes(info.id));
        renderFavourites();
      });

      card.appendChild(star);
    });
    renderFavourites();
  }

  // Remove only non-functional prototype wording from visible text nodes.
  function cleanPrototypeLabels() {
    const unwanted = [
      /^user interface$/i,
      /^runs locally$/i,
      /^running locally$/i,
      /^local interface$/i,
      /^prototype$/i,
      /^demo version$/i,
      /^test interface$/i,
      /^sample interface$/i
    ];

    const walker = document.createTreeWalker(
      document.body, NodeFilter.SHOW_TEXT
    );
    const nodes = [];
    while (walker.nextNode()) nodes.push(walker.currentNode);

    nodes.forEach(node => {
      const value = (node.nodeValue || "").trim();
      if (!value || !unwanted.some(rx => rx.test(value))) return;
      const parent = node.parentElement;
      if (!parent || parent.closest("button, input, textarea, select, script")) return;
      node.nodeValue = node.nodeValue.replace(value, "");
      if (!parent.textContent.trim()) parent.style.display = "none";
    });
  }

  // Re-run the entrance animation only after the password gate is submitted.
  function runSystemIgnition() {
    const selectors = [
      "main > *",
      "#main-system > *",
      ".main-content > *",
      ".dashboard > *",
      "#home > *"
    ];
    let items = [];
    selectors.forEach(selector => {
      document.querySelectorAll(selector).forEach(el => {
        if (el.closest("#login-screen, #lock-screen, .login-screen, .lock-screen")) return;
        if (el.id === "jat-favourites") return;
        const r = el.getBoundingClientRect();
        if (r.width > 0 && r.height > 0) items.push(el);
      });
    });
    items = [...new Set(items)];

    if (!items.length) {
      const main = document.querySelector(
        "#main-system, main, .main-content, .dashboard, #home"
      );
      if (main) items = [...main.children].filter(el =>
        !["SCRIPT", "STYLE"].includes(el.tagName) &&
        el.id !== "jat-favourites"
      );
    }

    items.forEach(el => {
      el.classList.remove("jat-startup-visible");
      el.classList.add("jat-startup-hidden");
    });

    // Allow the unlocked system to render before revealing each feature.
    setTimeout(() => {
      items.forEach((el, i) => {
        setTimeout(() => {
          el.classList.remove("jat-startup-hidden");
          el.classList.add("jat-startup-visible");
        }, i * 1150);
      });
    }, 350);
  }

  function initializeButton(target) {
    const button = target.closest("button, [role='button'], input[type='button'], input[type='submit']");
    if (!button) return false;
    const text = (
      button.innerText || button.value || button.getAttribute("aria-label") || ""
    ).trim().replace(/\s+/g, " ").toLowerCase();
    return text.includes("initialize system") || text === "initialize";
  }

  document.addEventListener("click", event => {
    if (!initializeButton(event.target)) return;
    setTimeout(() => {
      runSystemIgnition();
      setTimeout(addFavouriteControls, 500);
    }, 250);
  }, true);

  // Keep the existing terminal interface and its functions intact.
  // Refresh app discovery/favourite controls when the app list is populated.
  function refresh() {
    cleanPrototypeLabels();
    addFavouriteControls();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", refresh, { once: true });
  } else {
    refresh();
  }

  const observer = new MutationObserver(() => {
    if (window.__jatFavouriteRefreshPending) return;
    window.__jatFavouriteRefreshPending = true;
    requestAnimationFrame(() => {
      window.__jatFavouriteRefreshPending = false;
      addFavouriteControls();
      cleanPrototypeLabels();
    });
  });

  observer.observe(document.body, { childList: true, subtree: true });
})();
