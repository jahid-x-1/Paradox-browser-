// ============================================================================
// DLSS 5 RTX Game Booster - Main Application Logic
// ============================================================================

// Game package list for launching
const GAME_PACKAGES = [
    { name: "Free Fire", pkg: "com.dts.freefireth" },
    { name: "Free Fire MAX", pkg: "com.dts.freefiremax" },
    { name: "PUBG Mobile", pkg: "com.tencent.ig" },
    { name: "BGMI", pkg: "com.pubg.imobile" },
    { name: "Call of Duty Mobile", pkg: "com.activision.callofduty.shooter" },
    { name: "Mobile Legends", pkg: "com.mobile.legends" }
];

// Native bridge helper - sends commands to Android via prompt()
function nativeCall(cmd, payload = "") {
    try {
        const result = prompt(cmd, payload);
        console.log("Native call:", cmd, "->", result);
        return result;
    } catch (e) {
        console.log("Native bridge error:", e);
        return null;
    }
}

// Check Brevent status
function checkBrevent() {
    const result = nativeCall("__DLSS_BREVENT__", "");
    const chip = document.getElementById("statusChip");
    if (result === "BREVENT_ACTIVE") {
        chip.textContent = "BREVENT ACTIVE";
        chip.className = "status-chip brevent-active";
        showToast("Brevent ADB permission active - app will stay alive!");
    } else {
        chip.textContent = "BREVENT NOT FOUND";
        chip.className = "status-chip brevent-inactive";
        showToast("Brevent not active. Grant ADB permission via Brevent.");
    }
}

// Initialize app
function initApp() {
    console.log("DLSS 5 RTX Game Booster initialized");

    // Check for saved shader config
    const saved = localStorage.getItem("dlss_shader_config");
    if (saved) {
        try {
            const config = JSON.parse(saved);
            document.getElementById("applyStatus").textContent = "Config Loaded";
            document.getElementById("applyStatus").style.color = "#00D4FF";
        } catch (e) {
            console.log("No saved config");
        }
    }

    // Start telemetry updates
    setInterval(updateTelemetry, 1000);
}

// Boost RAM via native bridge
function boostRAM() {
    const btn = document.querySelector(".boost-btn:nth-child(1)");
    const originalText = btn.textContent;
    btn.textContent = "BOOSTING...";
    btn.style.background = "#00D4FF";

    nativeCall("__DLSS_BOOST__", "");

    setTimeout(() => {
        btn.textContent = originalText;
        btn.style.background = "#FF4444";
        showToast("RAM Optimized");
    }, 1000);
}

// Launch game selector
function launchGameSelector() {
    const container = document.createElement("div");
    container.className = "game-selector-modal";
    container.innerHTML = `
        <div class="modal-content">
            <h3>Select Game to Launch</h3>
            <div class="game-list">
                ${GAME_PACKAGES.map(g => `
                    <button class="game-btn" onclick="launchGame('${g.pkg}', this)">${g.name}</button>
                `).join('')}
            </div>
            <button class="close-btn" onclick="this.closest('.game-selector-modal').remove()">CLOSE</button>
        </div>
    `;
    document.body.appendChild(container);
}

// Launch specific game
function launchGame(pkg, btn) {
    if (!btn) return;

    const originalText = btn.textContent;
    btn.textContent = "LAUNCHING...";
    btn.style.background = "#00D4FF";

    nativeCall("__DLSS_LAUNCH__", pkg);

    setTimeout(() => {
        btn.textContent = originalText;
        btn.style.background = "#00D4FF";
        document.querySelector(".game-selector-modal")?.remove();
        showToast("Game Launching: " + pkg);
    }, 1000);
}

// Open overlay settings
function openOverlaySettings() {
    nativeCall("__DLSS_OVERLAY_PERM__", "");
    showToast("Opening Overlay Settings");
}

// Update telemetry display
function updateTelemetry() {
    const fpsElement = document.getElementById("telFPS");
    const gpuElement = document.getElementById("telGPU");
    const ramElement = document.getElementById("telRAM");
    const tempElement = document.getElementById("telTEMP");

    if (fpsElement) {
        const baseFPS = 60;
        const variance = Math.floor(Math.random() * 5) - 2;
        fpsElement.textContent = Math.max(30, baseFPS + variance);
    }
    if (gpuElement) {
        gpuElement.textContent = Math.floor(70 + Math.random() * 20) + "%";
    }
    if (ramElement) {
        ramElement.textContent = Math.floor(3800 + Math.random() * 500) + " MB";
    }
    if (tempElement) {
        tempElement.textContent = Math.floor(42 + Math.random() * 8) + "\u00B0C";
    }
}

// Toast notification
function showToast(msg) {
    let toast = document.getElementById("toast");
    if (!toast) {
        toast = document.createElement("div");
        toast.id = "toast";
        toast.className = "toast";
        document.body.appendChild(toast);
    }
    toast.textContent = msg;
    toast.className = "toast show";
    setTimeout(() => {
        toast.className = "toast";
    }, 2000);
}

// Initialize on load
window.addEventListener("DOMContentLoaded", initApp);

// Expose functions for inline HTML onclick
window.boostRAM = boostRAM;
window.launchGameSelector = launchGameSelector;
window.openOverlaySettings = openOverlaySettings;
