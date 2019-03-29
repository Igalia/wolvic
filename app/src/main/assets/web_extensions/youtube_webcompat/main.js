// Add meta-viewport
let viewport = document.head.querySelector("meta[name='viewport']");
if (!viewport) {
    viewport = document.createElement("meta");
    viewport.name = "viewport";
    viewport.content = "width=user-width, initial-scale=1";
    document.head.appendChild(viewport);
}
