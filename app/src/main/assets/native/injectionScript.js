(() => {
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
    const style = document.createElement('style');
    style.innerHTML = `
        .skinHeader {
            padding-top: 60px !important;
        }
        .headerTop {
            padding-top: 10px !important;
        }
        .mainDrawerButton, .headerRight, .headerLeft {
            margin-top: 10px !important;
        }
    `;
    document.head.appendChild(style);
    document.currentScript.remove();
})();
