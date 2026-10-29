// ============================================================================
// DLSS 5 RTX Neural Shader Pipeline - Shader Presets & Controls
// ============================================================================

// DLSS 5 Neural Shader Presets
const DLSS_PRESETS = {
    PERFORMANCE: {
        name: "Performance",
        desc: "Max FPS, minimal quality",
        values: [0.7, 0.6, 0.5, 0.6, 0.5, 0.4, 0.5, 0.6, 0.4, 0.3, 0.5, 0.6, 0.4, 0.5, 0.3, 0.4],
        color: "#00FF88"
    },
    BALANCED: {
        name: "Balanced",
        desc: "Good FPS & quality",
        values: [0.8, 0.7, 0.7, 0.8, 0.7, 0.6, 0.7, 0.8, 0.6, 0.5, 0.7, 0.8, 0.6, 0.7, 0.5, 0.6],
        color: "#FFFF00"
    },
    QUALITY: {
        name: "Quality",
        desc: "High visual fidelity",
        values: [0.9, 0.8, 0.8, 0.9, 0.8, 0.7, 0.8, 0.9, 0.8, 0.7, 0.8, 0.9, 0.8, 0.9, 0.7, 0.8],
        color: "#00D4FF"
    },
    ULTRA: {
        name: "Ultra",
        desc: "Maximum visual quality",
        values: [1.0, 0.9, 0.9, 1.0, 0.9, 0.8, 0.9, 1.0, 0.9, 0.8, 0.9, 1.0, 0.9, 1.0, 0.8, 0.9],
        color: "#FF00FF"
    },
    DLSS5: {
        name: "DLSS 5",
        desc: "Neural rendering enabled",
        values: [1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0],
        color: "#00FF88"
    }
};

// 16 Shader Control Labels
const SHADER_LABELS = [
    "Neural Scale", "Temporal AA", "Ray Trace", "Reflection",
    "Refraction", "Ambient Occ.", "Bloom", "Motion Blur",
    "Color Grade", "Sharpening", "Contrast", "Saturation",
    "Exposure", "Gamma", "Dithering", "Film Grain"
];

// Current state
let currentPreset = "CUSTOM";
let currentValues = [0.8, 0.7, 0.7, 0.8, 0.7, 0.6, 0.7, 0.8, 0.6, 0.5, 0.7, 0.8, 0.6, 0.7, 0.5, 0.6];
let customSlots = [
    [0.8, 0.7, 0.7, 0.8, 0.7, 0.6, 0.7, 0.8, 0.6, 0.5, 0.7, 0.8, 0.6, 0.7, 0.5, 0.6],
    [0.9, 0.8, 0.8, 0.9, 0.8, 0.7, 0.8, 0.9, 0.8, 0.7, 0.8, 0.9, 0.8, 0.9, 0.7, 0.8],
    [1.0, 0.9, 0.9, 1.0, 0.9, 0.8, 0.9, 1.0, 0.9, 0.8, 0.9, 1.0, 0.9, 1.0, 0.8, 0.9]
];

// HUD & Aura State
let hudEnabled = false;
let auraEnabled = false;
let auraColor = "#00D4FF";

// Initialize presets
function initPresets() {
    const grid = document.getElementById("presetGrid");
    const presetNames = Object.keys(DLSS_PRESETS);

    presetNames.forEach(key => {
        const p = DLSS_PRESETS[key];
        const btn = document.createElement("button");
        btn.className = "preset-btn";
        btn.style.borderLeft = "4px solid " + p.color;
        btn.onclick = () => applyPreset(key);
        btn.innerHTML = `
            <div class="preset-name">${p.name}</div>
            <div class="preset-desc">${p.desc}</div>
        `;
        grid.appendChild(btn);
    });
}

// Apply preset
function applyPreset(key) {
    if (key === "CUSTOM") {
        currentPreset = "CUSTOM";
        document.getElementById("presetBadge").textContent = "CUSTOM";
        document.getElementById("presetBadge").style.background = "#666";
        return;
    }

    const p = DLSS_PRESETS[key];
    currentValues = [...p.values];
    currentPreset = key;

    document.getElementById("presetBadge").textContent = key;
    document.getElementById("presetBadge").style.background = p.color;

    // Update sliders
    initSliders();

    // Apply via native bridge
    nativeCall("__DLSS_APPLY_SHADER__", JSON.stringify(currentValues));

    showToast("Applied " + p.name + " preset");
}

// Initialize sliders
function initSliders() {
    const grid = document.getElementById("sliderGrid");
    grid.innerHTML = "";

    SHADER_LABELS.forEach((label, i) => {
        const container = document.createElement("div");
        container.className = "slider-item";

        const labelDiv = document.createElement("div");
        labelDiv.className = "slider-label";
        labelDiv.textContent = label;

        const valueDiv = document.createElement("div");
        valueDiv.className = "slider-value";
        valueDiv.textContent = Math.round(currentValues[i] * 100) + "%";
        valueDiv.id = "val" + i;

        const slider = document.createElement("input");
        slider.type = "range";
        slider.min = "0";
        slider.max = "100";
        slider.value = Math.round(currentValues[i] * 100);
        slider.className = "slider-range";
        slider.oninput = (e) => {
            const v = parseInt(e.target.value) / 100;
            currentValues[i] = v;
            valueDiv.textContent = e.target.value + "%";
            document.getElementById("presetBadge").textContent = "CUSTOM";
            document.getElementById("presetBadge").style.background = "#666";
            currentPreset = "CUSTOM";
        };

        container.appendChild(labelDiv);
        container.appendChild(valueDiv);
        container.appendChild(slider);
        grid.appendChild(container);
    });
}

// Custom slot functions
function loadCustomSlot(slot) {
    currentValues = [...customSlots[slot]];
    initSliders();
    document.getElementById("presetBadge").textContent = "SLOT " + (slot + 1);
    document.getElementById("presetBadge").style.background = "#FFD700";
    currentPreset = "SLOT " + (slot + 1);

    nativeCall("__DLSS_APPLY_SHADER__", JSON.stringify(currentValues));

    showToast("Loaded Custom Slot " + (slot + 1));
}

function saveCustomSlot(slot) {
    customSlots[slot] = [...currentValues];
    showToast("Saved to Custom Slot " + (slot + 1));
}

// HUD toggle
function toggleHUD() {
    hudEnabled = !hudEnabled;
    const btn = document.getElementById("hudToggle");
    const badge = document.getElementById("hudBadge");

    if (hudEnabled) {
        btn.textContent = "DISABLE";
        btn.style.background = "#00D4FF";
        badge.textContent = "ON";
        badge.style.background = "#00D4FF";
        nativeCall("__DLSS_OVERLAY_ON__", "1");
        showToast("Floating HUD Enabled");
    } else {
        btn.textContent = "ENABLE";
        btn.style.background = "#666";
        badge.textContent = "OFF";
        badge.style.background = "#666";
        nativeCall("__DLSS_OVERLAY_OFF__", "");
        showToast("Floating HUD Disabled");
    }

    updateAuraPreview();
}

// Aura toggle
function toggleAura() {
    auraEnabled = !auraEnabled;
    const btn = document.getElementById("auraToggle");
    const badge = document.getElementById("hudBadge");

    if (auraEnabled) {
        btn.textContent = "DISABLE";
        btn.style.background = "#FF00FF";
        badge.textContent = "ON";
        badge.style.background = "#FF00FF";
        nativeCall("__DLSS_OVERLAY_ON__", "2");
        showToast("Aura Enabled");
    } else {
        btn.textContent = "ENABLE";
        btn.style.background = "#666";
        badge.textContent = "OFF";
        badge.style.background = "#666";
        nativeCall("__DLSS_OVERLAY_OFF__", "");
        showToast("Aura Disabled");
    }

    updateAuraPreview();
}

function setAuraColor(color) {
    auraColor = color;
    updateAuraPreview();

    // Update active color button
    document.querySelectorAll(".color-btn").forEach(b => b.style.border = "2px solid transparent");
    if (event && event.target) event.target.style.border = "2px solid white";

    nativeCall("__DLSS_APPLY_SHADER__", JSON.stringify({ auraColor: color }));

    showToast("Aura color set");
}

function updateAuraPreview() {
    const preview = document.getElementById("auraPreview");
    const glow = document.querySelector(".aura-glow");
    const hud = document.querySelector(".hud-preview");

    if (hudEnabled || auraEnabled) {
        preview.style.display = "block";
        if (glow) {
            glow.style.boxShadow = "0 0 30px " + auraColor + ", 0 0 60px " + auraColor;
            glow.style.background = auraColor;
        }
        if (hud) {
            hud.style.color = auraColor;
            hud.style.textShadow = "0 0 10px " + auraColor;
        }
    } else {
        preview.style.display = "none";
    }
}

// Apply shader + HUD + aura to game
function applyToGame() {
    const shaderData = {
        values: currentValues,
        preset: currentPreset,
        hud: hudEnabled,
        aura: auraEnabled,
        auraColor: auraColor
    };

    nativeCall("__DLSS_APPLY_SHADER__", JSON.stringify(shaderData));

    const status = document.getElementById("applyStatus");
    status.textContent = "Applied to Game";
    status.style.color = "#00FF88";
    showToast("Shader + HUD + Aura applied to game");

    setTimeout(() => {
        status.textContent = "Ready";
        status.style.color = "#888";
    }, 2000);
}

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

// Expose nativeCall globally
window.nativeCall = nativeCall;

// Expose functions for inline HTML onclick
window.applyPreset = applyPreset;
window.loadCustomSlot = loadCustomSlot;
window.saveCustomSlot = saveCustomSlot;
window.toggleHUD = toggleHUD;
window.toggleAura = toggleAura;
window.setAuraColor = setAuraColor;
window.applyToGame = applyToGame;
