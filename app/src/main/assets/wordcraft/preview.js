// GWS+ wrapper around pinned WordCraft v0.8.0 with the Android texture-limit patch.
// Only one local document is exposed. No remote URL, native bridge or storage.
const timeout = setTimeout(() => { document.title = "gws:error"; }, 45000);
try {
    const { default: init } = await import("/engine/wordcraft.js");
    await init({ module_or_path: "/engine/wordcraft_bg.wasm" });
    await new Promise((resolve, reject) => {
        const tick = () => {
            const loading = document.getElementById("wordcraft_loading");
            if (!loading) return resolve();
            if (loading.querySelector("p")) return reject(new Error("renderer"));
            setTimeout(tick, 100);
        };
        tick();
    });
    const response = await fetch("/document", { cache: "no-store" });
    if (!response.ok) throw new Error("document");
    const bytes = await response.arrayBuffer();
    if (bytes.byteLength > 10 * 1024 * 1024) throw new Error("size");
    const ext = new URLSearchParams(location.search).get("ext");
    if (!/^(docx|doc|odt|rtf|txt|md|html|htm|tex)$/.test(ext)) throw new Error("format");
    const transfer = new DataTransfer();
    // A neutral name keeps upstream diagnostic messages free of family filenames.
    transfer.items.add(new File([bytes], `document.${ext}`, { type: "application/octet-stream" }));
    const canvas = document.getElementById("wordcraft_canvas");
    canvas.dispatchEvent(new DragEvent("drop", { bubbles: true, cancelable: true, dataTransfer: transfer }));
    clearTimeout(timeout);
    document.title = "gws:ready";
} catch (error) {
    window.gwsStartupError = String(error);
    clearTimeout(timeout);
    document.title = "gws:error";
}
