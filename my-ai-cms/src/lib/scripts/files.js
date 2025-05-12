document.addEventListener('DOMContentLoaded', function() {
    // Elements
    const filesContainer = document.getElementById('files-container');
    const fileUpload = document.getElementById('fileUpload');
    const uploadFileTitle = document.getElementById('uploadFileTitle');
    const uploadFileBtn = document.getElementById('uploadFileBtn');
    const extensionFilter = document.getElementById('extensionFilter');
    const applyFilter = document.getElementById('applyFilter');
    const contextMenu = document.getElementById('contextMenu');
    const contextRename = document.getElementById('contextRename');
    const contextDelete = document.getElementById('contextDelete');

    // State
    let currentFileId = null;
    let currentFileTitle = null;

    // Initialize
    loadFiles();

    // Event listeners
    uploadFileBtn.addEventListener('click', uploadFile);
    applyFilter.addEventListener('click', applyExtensionFilter);
    contextRename.addEventListener('click', renameCurrentFile);
    contextDelete.addEventListener('click', deleteCurrentFile);

    // Close context menu when clicking elsewhere
    document.addEventListener('click', function() {
        hideContextMenu();
    });

    // Prevent context menu from closing when clicked
    contextMenu.addEventListener('click', function(e) {
        e.stopPropagation();
    });

    // Functions
    function loadFiles(extensions = null) {
        filesContainer.innerHTML = '<tr><td colspan="2" class="loading">Loading files...</td></tr>';

        let url = 'api/files.php?action=list';
        if (extensions) {
            url += '&extension=' + extensions;
        }

        fetch(url)
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    renderFileList(data.files);
                } else {
                    filesContainer.innerHTML = '<tr><td colspan="2" class="error">Error loading files: ' + data.message + '</td></tr>';
                }
            })
            .catch(error => {
                console.error('Error fetching files:', error);
                filesContainer.innerHTML = '<tr><td colspan="2" class="error">Error loading files</td></tr>';
            });
    }

    function renderFileList(files) {
        if (!files || files.length === 0) {
            filesContainer.innerHTML = '<tr><td colspan="2" class="empty">No files found</td></tr>';
            return;
        }

        filesContainer.innerHTML = '';
        files.forEach(file => {
            // Ensure file is a string
            const row = document.createElement('tr');
            row.className = 'file-item';
            row.dataset.fileId = file.id;

            // Add file type icon based on extension
            const extension = file.extension;
            const icon = getIconForExtension(extension);

            // Create title cell
            const titleCell = document.createElement('td');
            titleCell.className = 'file-title';
            titleCell.innerHTML = `<span class="file-type-icon">${icon}</span> ${file.title}`;
            titleCell.addEventListener('contextmenu', (e) => {
                e.preventDefault();
                showContextMenu(e.pageX, e.pageY, file.id,file.title);
            });

            // Create preview cell
            const previewCell = document.createElement('td');
            previewCell.className = 'file-preview';

            // If it's an image, show preview
            if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].includes(extension)) {
                previewCell.innerHTML = `<img src="api/files.php?action=get&id=${file.id}" width="100" alt="${file.title}">`;
            } else {
                previewCell.textContent = 'No preview';
            }

            // Actions cell
            const actionsCell = document.createElement('td');
            actionsCell.className = 'file-actions';

            // Download button
            const downloadBtn = document.createElement('button');
            downloadBtn.className = 'action-btn download-btn';
            downloadBtn.textContent = 'Download';
            downloadBtn.addEventListener('click', function() {
                downloadFile(file.id, file.title, extension);
            });

            actionsCell.appendChild(downloadBtn);
            //
            // // Add context menu to row
            // row.addEventListener('contextmenu', function(e) {
            //     showContextMenu(e, file.id, file.title);
            // });

            row.appendChild(titleCell);
            row.appendChild(previewCell);
            row.appendChild(actionsCell);
            filesContainer.appendChild(row);
        });
    }

    function getIconForExtension(extension) {
        switch(extension.toLowerCase()) {
            case 'txt': return '📄';
            case 'md': return '📝';
            case 'json': return '📋';
            case 'html': return '🌐';
            case 'css': return '🎨';
            case 'js': return '⚙️';
            case 'php': return '🐘';
            case 'jpg':
            case 'jpeg':
            case 'png':
            case 'gif':
            case 'webp':
            case 'svg': return '🖼️';
            default: return '📄';
        }
    }

    function showContextMenu(x,y, fileId, title) {

        contextMenu.style.left = x + 'px';
        contextMenu.style.top = y + 'px';
        contextMenu.style.display = 'block';
        // Update current file
        currentFileId = fileId;
        currentFileTitle = title;
    }

    // Function to hide context menu
    function hideContextMenu() {
        contextMenu.style.display = 'none';
    }


    function deleteCurrentFile() {
        if (!currentFileId) return;

        const confirm = window.confirm(`Are you sure you want to delete ${currentFileTitle}?`);
        if (!confirm) return;

        fetch(`api/files.php?action=delete&id=${currentFileId}`, {
            method: 'GET'
        })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    // Reset state
                    currentFileId = null;
                    currentFileTitle=null;

                    // Hide context menu
                    contextMenu.style.display = 'none';

                    // Reload file list
                    loadFiles(extensionFilter.value || null);

                    alert('File deleted successfully');
                } else {
                    alert('Error deleting file: ' + data.message);
                }
            })
            .catch(error => {
                console.error('Error deleting file:', error);
                alert('Network error when deleting file');
            });
    }

    function renameCurrentFile() {
        if (!currentFileId) return;
        const newTitle = prompt('Enter new title for the file:', currentFileTitle);
        if (!newTitle || newTitle === currentFileTitle) return;

        fetch('api/files.php?action=update', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                id: currentFileId,
                title: newTitle
            })
        })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    // Reset state
                    currentFileId = null;
                    currentFileTitle = null;

                    // Hide context menu
                    contextMenu.style.display = 'none';

                    // Reload file list
                    loadFiles(extensionFilter.value || null);

                    alert('File renamed successfully');
                } else {
                    alert('Error renaming file: ' + data.message);
                }
            })
            .catch(error => {
                console.error('Error renaming file:', error);
                alert('Network error when renaming file');
            });
    }

    function uploadFile() {
        const title = uploadFileTitle.value.trim();
        const file = fileUpload.files[0];

        if (!title) {
            alert('Please enter a file title');
            return;
        }

        if (!file) {
            alert('Please select a file to upload');
            return;
        }
        const extension = file.name.split('.').pop().toLowerCase();
        const reader = new FileReader();
        reader.onload = function(event) {
            // Get base64 content (removing the data:mime/type;base64, prefix)
            const base64Content = event.target.result.split(',')[1];

            fetch('api/files.php?action=create', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    title: title,
                    content: base64Content,
                    originalFilename: file.name,
                    extension:extension
                })
            })
                .then(response => response.json())
                .then(data => {
                    if (data.success) {
                        // Reset form
                        uploadFileTitle.value = '';
                        fileUpload.value = '';

                        // Reload file list
                        loadFiles(extensionFilter.value || null);

                        alert('File uploaded successfully');
                    } else {
                        alert('Error uploading file: ' + data.message);
                    }
                })
                .catch(error => {
                    console.error('Error uploading file:', error);
                    alert('Network error when uploading file');
                });
        };

        reader.readAsDataURL(file);
    }

    function applyExtensionFilter() {
        const extensions = extensionFilter.value.trim();
        loadFiles(extensions || null);
    }

    // Function to download a file
    function downloadFile(fileId, fileTitle, fileExtension) {
        // Create a download URL
        const downloadUrl = `api/files.php?action=get&id=${fileId}`;

        // Create a temporary anchor element
        const downloadLink = document.createElement('a');
        downloadLink.href = downloadUrl;

        // Set download attribute with filename
        const fileName = fileExtension ? `${fileTitle}.${fileExtension}` : fileTitle;
        downloadLink.setAttribute('download', fileName);

        // Append to body, click and remove
        document.body.appendChild(downloadLink);
        downloadLink.click();
        document.body.removeChild(downloadLink);
    }
});