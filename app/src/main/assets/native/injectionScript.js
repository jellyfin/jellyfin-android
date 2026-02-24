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
        /* Main Header adjustments */
        .skinHeader {
            padding-top: 64px !important;
        }
        .headerTop {
            padding-top: 10px !important;
        }
        .mainDrawerButton, .headerRight, .headerLeft {
            margin-top: 10px !important;
        }

        /* Side menu (Drawer) adjustments */
        .mainDrawer,
        .mainDrawer-scrollContainer,
        .drawerContent,
        div[data-role="panel"] {
            padding-top: 64px !important;
        }

        /* Push content down ONLY on Home and Library pages to prevent cutoff */
        #indexPage, .libraryPage, .moviesPage, .tvPage {
            margin-top: 40px !important;
        }

        /* Ensure detail pages don't have the extra margin that shifts images */
        .itemDetailPage {
            margin-top: 0 !important;
        }

        .mainDrawer {
            background-clip: padding-box;
        }
    `;
    document.head.appendChild(style);
    document.currentScript.remove();
})();
