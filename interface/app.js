"use strict";

const $ = id => document.getElementById(id);
let installedApps = [];
let activeCategory = "all";
let currentFolderId = null;
let contextApp = null;
let toastTimer = null;
let streamTimer = null;
let matrixTimer = null;
let longPressTimer = null;
let longPressTriggered = false;

const KEYS = {
    favourites: "jat_favourites",
    folders: "jat_user_folders",
    notes: "jat_notes",
    appearance: "jat_appearance",
    hidden: "jat_hidden_apps"
};

function readJSON(key, fallback) {
    try {
        const value = JSON.parse(localStorage.getItem(key) || "null");
        return value === null ? fallback : value;
    } catch (_) { return fallback; }
}
function writeJSON(key, value) {
    localStorage.setItem(key, JSON.stringify(value));
}
function notify(message) {
    const el = $("toast");
    if (!el) return;
    el.textContent = message;
    el.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove("show"), 2100);
}
function deviceSettings() {
    if (window.JAT && JAT.openDeviceSettings) JAT.openDeviceSettings();
    else notify("Device settings unavailable");
}
function launchApp(app) {
    if (!app) return;
    if (window.JAT && JAT.launchApp) {
        const ok = JAT.launchApp(app.package, app.activity);
        if (ok === false) notify("Unable to open " + app.name);
    } else notify("Android launcher bridge unavailable");
}
function appInfo(app) {
    if (window.JAT && JAT.openAppInfo) JAT.openAppInfo(app.package);
    else notify("App information unavailable");
}
function showScreen(id) {
    closeFolder();
    closeContext();
    document.querySelectorAll(".screen").forEach(s => s.classList.remove("active"));
    const target = $(id);
    if (target) target.classList.add("active");
    document.querySelectorAll(".nav-item").forEach(n => {
        n.classList.toggle("active", n.dataset.screen === id);
    });
    if (id === "applicationsScreen") {
        activeCategory = "all";
        renderCategoryFilters();
        renderApps();
    }
    if (id === "homeScreen") renderHome();
    if (id === "customizeScreen") renderManagedFolders();
    window.scrollTo(0, 0);
}
function goBackScreen() {
    if (!$("folderModal").hidden) { closeFolder(); return; }
    if (!$("contextOverlay").hidden) { closeContext(); return; }
    const active = document.querySelector(".screen.active");
    if (active && active.id !== "homeScreen") showScreen("homeScreen");
}
function loadApps() {
    if (!window.JAT || typeof JAT.getApps !== "function") {
        installedApps = [];
        updateCounts();
        renderEverything();
        notify("Application index unavailable");
        return;
    }
    try {
        const result = JSON.parse(JAT.getApps() || "[]");
        installedApps = Array.isArray(result) ? result.filter(a =>
            a && a.name && a.package && a.activity
        ) : [];
        installedApps.sort((a,b) => a.name.localeCompare(b.name));
        updateCounts();
        renderEverything();
    } catch (error) {
        installedApps = [];
        updateCounts();
        renderEverything();
        notify("Could not read application index");
    }
}
function refreshApps() {
    loadApps();
    notify("Application index rebuilt");
}
function updateCounts() {
    $("appCount").textContent = installedApps.length + " APPS";
    $("appTotal").textContent = installedApps.length + " APPLICATIONS";
    if (window.JAT && JAT.getDeviceName) {
        $("deviceName").textContent = String(JAT.getDeviceName()).toUpperCase();
    }
}
function getFavourites() {
    const value = readJSON(KEYS.favourites, []);
    return Array.isArray(value) ? value : [];
}
function setFavourite(pkg, enabled) {
    let list = getFavourites().filter(x => x !== pkg);
    if (enabled) list.push(pkg);
    writeJSON(KEYS.favourites, list);
    renderEverything();
}
function getHidden() {
    const value = readJSON(KEYS.hidden, []);
    return Array.isArray(value) ? value : [];
}
function setHidden(pkg, enabled) {
    let list = getHidden().filter(x => x !== pkg);
    if (enabled) list.push(pkg);
    writeJSON(KEYS.hidden, list);
    renderEverything();
}
function getFolders() {
    const value = readJSON(KEYS.folders, []);
    if (!Array.isArray(value)) return [];
    return value.filter(f => f && f.id && f.name).map(f => ({
        id:String(f.id),
        name:String(f.name),
        apps:Array.isArray(f.apps) ? f.apps : [],
        color:f.color || "green"
    }));
}
function saveFolders(folders) {
    writeJSON(KEYS.folders, folders);
    renderEverything();
}
function folderColor(color) {
    return ({
        green:"#8dc79a", amber:"#c7a36b",
        blue:"#75a9c9", red:"#bd7770"
    })[color] || "#8dc79a";
}
function getAppByPackage(pkg) {
    return installedApps.find(a => a.package === pkg);
}
function appCategory(app) {
    const p = (app.package + " " + app.name).toLowerCase();
    if (/game|pubg|minecraft|roblox|freefire|clash|asphalt|genshin/.test(p)) return "Games";
    if (/whatsapp|telegram|messenger|signal|discord|skype|phone|contacts|message|gmail|outlook/.test(p)) return "Communication";
    if (/youtube|spotify|netflix|primevideo|mxplayer|vlc|camera|gallery|photos|music|video/.test(p)) return "Media";
    if (/drive|docs|sheets|slides|office|notion|calendar|keep|notes|word|excel|pdf|classroom/.test(p)) return "Productivity";
    if (/settings|systemui|packageinstaller|filemanager|files|calculator|clock|browser|chrome|firefox/.test(p)) return "Tools";
    return "Other";
}
function getSortedApps(list) {
    const order = $("sortOrder")?.value || "az";
    return [...list].sort((a,b) => {
        const c = a.name.localeCompare(b.name);
        return order === "za" ? -c : c;
    });
}
function makeAppCard(app) {
    const card = document.createElement("button");
    card.type = "button";
    card.className = "app-card";
    card.setAttribute("aria-label", app.name);
    card.dataset.package = app.package;

    if (app.icon && app.icon.startsWith("data:image/")) {
        const img = document.createElement("img");
        img.className = "app-icon";
        img.alt = "";
        img.src = app.icon;
        img.onerror = () => {
            const fallback = makeFallback(app);
            img.replaceWith(fallback);
        };
        card.appendChild(img);
    } else card.appendChild(makeFallback(app));

    const label = document.createElement("span");
    label.className = "app-name";
    label.textContent = app.name;
    card.appendChild(label);

    card.addEventListener("click", () => {
        if (longPressTriggered) {
            longPressTriggered = false;
            return;
        }
        launchApp(app);
    });

    card.addEventListener("pointerdown", event => {
        if (event.button !== undefined && event.button !== 0) return;
        longPressTriggered = false;
        clearTimeout(longPressTimer);
        longPressTimer = setTimeout(() => {
            longPressTriggered = true;
            openAppContext(app, event.clientX, event.clientY);
        }, 520);
    });
    ["pointerup","pointerleave","pointercancel"].forEach(name =>
        card.addEventListener(name, () => clearTimeout(longPressTimer))
    );
    card.addEventListener("contextmenu", event => {
        event.preventDefault();
        openAppContext(app, event.clientX, event.clientY);
    });
    return card;
}
function makeFallback(app) {
    const el = document.createElement("div");
    el.className = "app-fallback";
    el.textContent = (app.name || "?").slice(0,1).toUpperCase();
    return el;
}
function renderApps() {
    const container = $("allApps");
    if (!container) return;
    container.replaceChildren();

    const query = ($("appSearch").value || "").trim().toLowerCase();
    const showHidden = $("showHidden").checked;
    let list = installedApps.filter(app => {
        const hidden = getHidden().includes(app.package);
        if (hidden && !showHidden) return false;
        const matches = app.name.toLowerCase().includes(query) ||
            app.package.toLowerCase().includes(query);
        if (!matches) return false;

        if (activeCategory === "all") return true;
        if (activeCategory === "favourites") return getFavourites().includes(app.package);
        if (activeCategory.startsWith("folder:")) {
            const id = activeCategory.slice(7);
            const folder = getFolders().find(f => f.id === id);
            return !!folder && folder.apps.includes(app.package);
        }
        return appCategory(app) === activeCategory;
    });

    list = getSortedApps(list);
    list.forEach(app => container.appendChild(makeAppCard(app)));
    $("emptyApps").hidden = list.length > 0;
    $("appTotal").textContent = list.length + " OF " + installedApps.length + " APPLICATIONS";
}
function renderCategoryFilters() {
    const container = $("categoryFilters");
    if (!container) return;
    container.replaceChildren();

    const categories = ["all","favourites","Communication","Media","Productivity","Games","Tools","Other"];
    const labels = {
        all:"ALL", favourites:"★ PINNED"
    };
    categories.forEach(category => {
        const button = document.createElement("button");
        button.className = "category-chip" + (activeCategory === category ? " active" : "");
        button.textContent = labels[category] || category.toUpperCase();
        button.addEventListener("click", () => {
            activeCategory = category;
            renderCategoryFilters();
            renderApps();
        });
        container.appendChild(button);
    });

    getFolders().forEach(folder => {
        const button = document.createElement("button");
        const key = "folder:" + folder.id;
        button.className = "category-chip" + (activeCategory === key ? " active" : "");
        button.textContent = "▣ " + folder.name.toUpperCase();
        button.addEventListener("click", () => {
            activeCategory = key;
            renderCategoryFilters();
            renderApps();
        });
        container.appendChild(button);
    });
}
function renderFavourites() {
    const container = $("favourites");
    container.replaceChildren();
    const list = getFavourites().map(getAppByPackage).filter(Boolean);
    if (!list.length) {
        const empty = document.createElement("div");
        empty.className = "empty-state";
        empty.style.gridColumn = "1 / -1";
        empty.innerHTML = "<h3>NO PINNED APPLICATIONS</h3><p>Hold an app icon and select PIN TO HOME.</p>";
        container.appendChild(empty);
        return;
    }
    list.forEach(app => container.appendChild(makeAppCard(app)));
}
function renderFolders() {
    const container = $("homeFolders");
    container.replaceChildren();
    const folders = getFolders();

    if (!folders.length) {
        const empty = document.createElement("div");
        empty.className = "empty-state";
        empty.style.gridColumn = "1 / -1";
        empty.innerHTML = "<h3>NO DIRECTORIES</h3><p>Create a folder in Workspace.</p>";
        container.appendChild(empty);
        return;
    }

    folders.forEach(folder => {
        const card = document.createElement("button");
        card.type = "button";
        card.className = "folder-card";
        card.style.setProperty("--folder-color", folderColor(folder.color));

        const top = document.createElement("div");
        top.className = "folder-card-top";
        const symbol = document.createElement("span");
        symbol.className = "folder-symbol";
        symbol.textContent = "▣";
        const count = document.createElement("span");
        count.className = "folder-count";
        count.textContent = String(folder.apps.length).padStart(2,"0") + " APPS";
        top.append(symbol,count);

        const name = document.createElement("span");
        name.className = "folder-card-name";
        name.textContent = folder.name;

        const preview = document.createElement("span");
        preview.className = "folder-preview";
        folder.apps.slice(0,5).forEach(pkg => {
            const app = getAppByPackage(pkg);
            if (!app || !app.icon) return;
            const img = document.createElement("img");
            img.src = app.icon;
            img.alt = "";
            preview.appendChild(img);
        });

        card.append(top,name,preview);
        card.addEventListener("click", () => openFolder(folder.id));
        card.addEventListener("contextmenu", e => {
            e.preventDefault();
            openFolderContext(folder, e.clientX, e.clientY);
        });
        let timer;
        card.addEventListener("pointerdown", e => {
            timer = setTimeout(() => openFolderContext(folder,e.clientX,e.clientY),520);
        });
        ["pointerup","pointerleave","pointercancel"].forEach(ev =>
            card.addEventListener(ev,()=>clearTimeout(timer))
        );
        container.appendChild(card);
    });
}
function renderManagedFolders() {
    const container = $("managedFolders");
    if (!container) return;
    container.replaceChildren();

    getFolders().forEach(folder => {
        const row = document.createElement("div");
        row.className = "managed-folder-row";

        const name = document.createElement("input");
        name.value = folder.name;
        name.maxLength = 26;
        name.setAttribute("aria-label","Folder name");

        const color = document.createElement("select");
        ["green","amber","blue","red"].forEach(c => {
            const option = document.createElement("option");
            option.value = c;
            option.textContent = c.toUpperCase();
            color.appendChild(option);
        });
        color.value = folder.color;

        const remove = document.createElement("button");
        remove.textContent = "DELETE";
        remove.addEventListener("click", () => deleteFolder(folder.id));

        const save = () => {
            const folders = getFolders();
            const target = folders.find(f => f.id === folder.id);
            if (!target) return;
            const value = name.value.trim();
            if (value) target.name = value;
            target.color = color.value;
            saveFolders(folders);
        };
        name.addEventListener("change",save);
        color.addEventListener("change",save);
        row.append(name,color,remove);
        container.appendChild(row);
    });

    if (!getFolders().length) {
        const empty = document.createElement("div");
        empty.className = "note-status";
        empty.textContent = "NO DIRECTORIES CREATED";
        container.appendChild(empty);
    }
}
function createFolder() {
    const input = $("newFolderName");
    const name = input.value.trim();
    if (!name) { notify("Enter a folder name"); return; }

    const folders = getFolders();
    if (folders.some(f => f.name.toLowerCase() === name.toLowerCase())) {
        notify("Folder already exists");
        return;
    }
    folders.push({
        id:"folder_" + Date.now() + "_" + Math.random().toString(36).slice(2,7),
        name,
        apps:[],
        color:$("newFolderColor").value
    });
    input.value = "";
    saveFolders(folders);
    renderCategoryFilters();
    notify("Directory created");
}
function deleteFolder(id) {
    const folders = getFolders().filter(f => f.id !== id);
    saveFolders(folders);
    renderCategoryFilters();
    if (currentFolderId === id) closeFolder();
    notify("Directory removed");
}
function openFolder(id) {
    const folder = getFolders().find(f => f.id === id);
    if (!folder) return;
    currentFolderId = id;
    $("folderModalTitle").textContent = folder.name.toUpperCase();
    $("folderMeta").textContent = "ROOT / " + folder.name.toUpperCase() + " / " + folder.apps.length + " ITEMS";
    const container = $("folderModalApps");
    container.replaceChildren();
    container.classList.toggle("list-mode", $("folderLayout").value === "list");

    const apps = folder.apps.map(getAppByPackage).filter(Boolean);
    apps.forEach(app => container.appendChild(makeAppCard(app)));
    $("folderModalEmpty").hidden = apps.length > 0;
    $("folderModal").hidden = false;
}
function closeFolder() {
    $("folderModal").hidden = true;
    currentFolderId = null;
}
function moveToFolder(app, id) {
    const folders = getFolders();
    folders.forEach(f => {
        f.apps = f.apps.filter(pkg => pkg !== app.package);
        if (f.id === id) f.apps.push(app.package);
    });
    saveFolders(folders);
    renderCategoryFilters();
    notify("Application moved");
}
function removeFromFolders(app) {
    const folders = getFolders();
    folders.forEach(f => f.apps = f.apps.filter(pkg => pkg !== app.package));
    saveFolders(folders);
    notify("Removed from directories");
}
function openAppContext(app,x,y) {
    contextApp = app;
    const overlay = $("contextOverlay");
    const menu = $("contextMenu");
    menu.replaceChildren();

    const title = document.createElement("div");
    title.className = "context-title";
    title.textContent = app.name;
    menu.appendChild(title);

    addContextAction(menu,"OPEN","↗",()=>{closeContext();launchApp(app);});
    addContextAction(menu,"APP INFO","ⓘ",()=>{closeContext();appInfo(app);});

    const pinned = getFavourites().includes(app.package);
    addContextAction(menu,pinned ? "REMOVE FROM HOME" : "PIN TO HOME","★",()=>{
        closeContext();
        setFavourite(app.package,!pinned);
        notify(pinned ? "Removed from home" : "Pinned to home");
    });

    const folders = getFolders();
    const current = folders.find(f => f.apps.includes(app.package));
    addContextAction(menu,current ? "CHANGE FOLDER" : "ADD TO FOLDER","▣",()=>{
        const old = menu.querySelector(".context-submenu");
        if (old) { old.remove(); return; }
        const sub = document.createElement("div");
        sub.className = "context-submenu";
        if (!folders.length) {
            const create = document.createElement("button");
            create.textContent = "CREATE A FOLDER IN WORKSPACE";
            create.addEventListener("click",()=>{closeContext();showScreen("customizeScreen");});
            sub.appendChild(create);
        } else {
            folders.forEach(folder => {
                const b = document.createElement("button");
                b.textContent = "▣  " + folder.name;
                b.addEventListener("click",()=>{closeContext();moveToFolder(app,folder.id);});
                sub.appendChild(b);
            });
        }
        if (current) {
            const remove = document.createElement("button");
            remove.textContent = "REMOVE FROM FOLDER";
            remove.addEventListener("click",()=>{closeContext();removeFromFolders(app);});
            sub.appendChild(remove);
        }
        menu.appendChild(sub);
        positionContext(x,y);
    });

    const hidden = getHidden().includes(app.package);
    addContextAction(menu,hidden ? "UNHIDE APPLICATION" : "HIDE APPLICATION","◌",()=>{
        closeContext();
        setHidden(app.package,!hidden);
        notify(hidden ? "Application visible" : "Application hidden");
    });
    positionContext(x,y);
    overlay.hidden = false;
}
function openFolderContext(folder,x,y) {
    const menu = $("contextMenu");
    menu.replaceChildren();
    const title = document.createElement("div");
    title.className = "context-title";
    title.textContent = folder.name;
    menu.appendChild(title);

    addContextAction(menu,"OPEN DIRECTORY","↗",()=>{closeContext();openFolder(folder.id);});
    addContextAction(menu,"EDIT WORKSPACE","◈",()=>{closeContext();showScreen("customizeScreen");});
    addContextAction(menu,"DELETE DIRECTORY","×",()=>{closeContext();deleteFolder(folder.id);},true);
    positionContext(x,y);
    $("contextOverlay").hidden = false;
}
function addContextAction(menu,label,symbol,callback,danger=false) {
    const button = document.createElement("button");
    button.className = "context-action" + (danger ? " danger" : "");
    const left = document.createElement("span");
    left.textContent = label;
    const right = document.createElement("span");
    right.textContent = symbol;
    button.append(left,right);
    button.addEventListener("click",callback);
    menu.appendChild(button);
}
function positionContext(x,y) {
    const menu = $("contextMenu");
    menu.style.left = Math.max(8,Math.min(x,innerWidth-245)) + "px";
    menu.style.top = Math.max(8,Math.min(y,innerHeight-menu.offsetHeight-12)) + "px";
}
function closeContext() {
    $("contextOverlay").hidden = true;
    contextApp = null;
}
function clearSearch() {
    $("appSearch").value = "";
    renderApps();
}
function saveAppearance() {
    const settings = {
        columns:$("gridColumns").value,
        iconSize:$("iconSize").value,
        labels:$("showLabels").checked,
        theme:$("themeMode").value,
        animations:$("animations").checked,
        folderLayout:$("folderLayout").value
    };
    writeJSON(KEYS.appearance,settings);
    applyAppearance();
}
function applyAppearance() {
    const settings = readJSON(KEYS.appearance,{});
    $("gridColumns").value = settings.columns || "4";
    $("iconSize").value = settings.iconSize || "medium";
    $("showLabels").checked = settings.labels !== false;
    $("themeMode").value = settings.theme || "hacker";
    $("animations").checked = settings.animations !== false;
    $("folderLayout").value = settings.folderLayout || "grid";

    document.documentElement.style.setProperty("--columns",settings.columns || "4");
    document.body.classList.toggle("hide-labels",settings.labels === false);
    document.body.classList.toggle("eye-comfort",settings.theme === "eye");
    document.body.classList.toggle("midnight",settings.theme === "midnight");
    document.body.classList.remove("size-small","size-medium","size-large");
    document.body.classList.add("size-" + (settings.iconSize || "medium"));
    $("matrix").style.display = settings.animations === false ? "none" : "block";
    renderEverything();
}
function resetAppearance() {
    localStorage.removeItem(KEYS.appearance);
    applyAppearance();
    notify("Appearance reset");
}
function saveNotes() {
    localStorage.setItem(KEYS.notes,$("notesArea").value);
    $("noteStatus").textContent = "UPDATED";
}
function updateClock() {
    const now = new Date();
    $("clock").textContent = now.toLocaleTimeString([], {hour:"2-digit",minute:"2-digit"});
    $("streamClock").textContent = now.toLocaleTimeString([], {hour12:false});
}
function renderEverything() {
    renderFavourites();
    renderFolders();
    renderManagedFolders();
    renderCategoryFilters();
    renderApps();
}
function renderHome() {
    renderFavourites();
    renderFolders();
}
function initMatrix() {
    const canvas = $("matrix");
    const ctx = canvas.getContext("2d");
    let width=0,height=0,drops=[];
    const fontSize=15;
    const chars="01アイウエオカキクケコ";

    function resize() {
        const dpr = Math.min(devicePixelRatio || 1,1.5);
        width = innerWidth;
        height = innerHeight;
        canvas.width = Math.floor(width*dpr);
        canvas.height = Math.floor(height*dpr);
        canvas.style.width = width+"px";
        canvas.style.height = height+"px";
        ctx.setTransform(dpr,0,0,dpr,0,0);
        drops = Array(Math.ceil(width/fontSize)).fill(0);
    }
    function draw() {
        if (canvas.style.display === "none") return;
        ctx.fillStyle = "rgba(8,13,10,.16)";
        ctx.fillRect(0,0,width,height);
        ctx.fillStyle = document.body.classList.contains("eye-comfort")
            ? "rgba(190,150,95,.42)" : "rgba(100,190,125,.48)";
        ctx.font = fontSize+"px monospace";
        for (let i=0;i<drops.length;i++) {
            ctx.fillText(chars[Math.floor(Math.random()*chars.length)],i*fontSize,drops[i]*fontSize);
            if (drops[i]*fontSize>height && Math.random()>.978) drops[i]=0;
            drops[i]++;
        }
    }
    resize();
    window.addEventListener("resize",resize);
    matrixTimer = setInterval(draw,115);
}
function startSystemStream() {
    const lines = [
        "APP_INDEX // READY",
        "DIRECTORY_TABLE // SYNCHRONIZED",
        "SESSION_CLOCK // ACTIVE",
        "INTERFACE_KERNEL // RUNNING",
        "INPUT_CHANNEL // LISTENING",
        "DISPLAY_PIPELINE // STABLE",
        "WORKSPACE // AVAILABLE",
        "JAT_CORE // ONLINE"
    ];
    let index=0;
    const host=$("systemStream");
    function tick() {
        const line=document.createElement("div");
        line.className="stream-line";
        line.textContent=lines[index++%lines.length];
        host.appendChild(line);
        while(host.children.length>4) host.removeChild(host.firstChild);
    }
    tick();
    streamTimer=setInterval(tick,2600);
}
window.JATSystemBoot = function() {
    const active = document.querySelector(".screen.active");
    const targets = [
        ...document.querySelectorAll(".topbar"),
        ...(active ? [...active.querySelectorAll(":scope > .boot-target")] : []),
        ...document.querySelectorAll(".bottom-nav")
    ];
    const unique=[...new Set(targets)];
    unique.forEach(el => {
        el.classList.remove("boot-visible");
        el.classList.add("boot-hidden");
    });
    unique.forEach((el,index) => {
        setTimeout(() => {
            el.classList.remove("boot-hidden");
            el.classList.add("boot-visible");
        },500+index*1150);
    });
};
document.addEventListener("DOMContentLoaded",()=>{
    $("appSearch").addEventListener("input",renderApps);
    $("notesArea").value=localStorage.getItem(KEYS.notes)||"";
    $("notesArea").addEventListener("input",saveNotes);
    $("contextOverlay").addEventListener("click",e=>{
        if(e.target===$("contextOverlay")) closeContext();
    });
    $("folderModal").addEventListener("click",e=>{
        if(e.target===$("folderModal")) closeFolder();
    });
    document.addEventListener("keydown",e=>{
        if(e.key==="Escape") goBackScreen();
    });
    applyAppearance();
    loadApps();
    updateClock();
    setInterval(updateClock,1000);
    initMatrix();
    startSystemStream();
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
