(() => {
    const style = document.createElement('style');
    style.textContent = `
        /* Title and text wrapping for long filenames/strings */
        .itemName, .parentName, .detailName, .primaryText, .secondaryText {
            white-space: normal !important;
            overflow-wrap: break-word !important;
            word-wrap: break-word !important;
            overflow: visible !important;
            hyphens: auto !important;
        }
        /* Item descriptions should always wrap */
        .itemDescription, .detailDescription {
            overflow-wrap: break-word !important;
            word-wrap: break-word !important;
            white-space: normal !important;
        }
    `;
    document.head.appendChild(style);

    const scripts = [
        '/native/nativeshell.js',
        '/native/EventEmitter.js',
        document.currentScript.src.concat('?deferred=true&ts=', Date.now())
    ];
    for (const script of scripts) {
        const scriptElement = document.createElement('script');
        scriptElement.src = script;
        scriptElement.charset = 'utf-8';
        scriptElement.setAttribute('defer', '');
        document.body.appendChild(scriptElement);
    }
    document.currentScript.remove();
})();
