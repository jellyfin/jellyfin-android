window.addEventListener('viewshow', async function () {
    if (location.hash.includes('/details?id=')) {
        let btnDownload = document.querySelectorAll('.btnDownload');
        let allowedTypes = ['Season', 'MusicAlbum'];
        let parentId = new URLSearchParams(location.hash.split('?').pop()).get('id');
        if (parentId && btnDownload.length && ApiClient) {
            let parent = await ApiClient.getItem(ApiClient.getCurrentUserId(), parentId) || {};
            if (allowedTypes.indexOf(parent.Type) != -1) {
                let items = await ApiClient.getItems(ApiClient.getCurrentUserId(), { ParentId: parentId, excludeLocationTypes: 'Virtual,Offline', Fields: 'CanDownload,Path', EnableImages: false, EnableUserData: false }) || {};
                if (items.Items) {
                    items = items.Items.flatMap(item => item.CanDownload && item.Path ? { title: item.Name, filename: item.Path.replace(/^.*[\\\/]/, ''), url: ApiClient.getItemDownloadUrl(item.Id) } : []);
                    if (items.length) {
                        btnDownload.forEach(btn => {
                            if (btn.classList.contains('hide')) {
                                btn.classList.remove('hide');
                            }
                            btn.outerHTML += '';
                        });
                        btnDownload = document.querySelectorAll('.btnDownload');
                        btnDownload.forEach(btn => btn.addEventListener('click', function () {
                            if (window.NativeShell) {
                                items.forEach(item => window.NativeShell.downloadFile(item));
                            }
                        }, false));
                    }
                }
            }
        }
    }
}, false);
