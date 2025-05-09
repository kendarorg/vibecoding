document.addEventListener('DOMContentLoaded', function() {
    // Elements
    const filesContainer = document.getElementById('files-container');
    const deleteBtn = document.getElementById('deleteBtn');
    const currentFileName = document.getElementById('currentFileName');
    const newFileTitle = document.getElementById('newFileTitle');
    const newFileExtension = document.getElementById('newFileExtension');
    const createFileBtn = document.getElementById('createFileBtn');
    const extensionFilter = document.getElementById('extensionFilter');
    const applyFilter = document.getElementById('applyFilter');

    // State
    let currentFileId = null;

    // Initialize
    loadFiles();

    // Event listeners
    deleteBtn.addEventListener('click', deleteCurrentFile);
    createFileBtn.addEventListener('click', createNewFile);
    applyFilter.addEventListener('click', applyExtensionFilter);

    // Functions
    function loadFiles(extensions = null) {
        filesContainer.innerHTML = '<div class="loading">Loading files...</div>';

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
                    filesContainer.innerHTML = '<div class="error">Error loading files: ' + data.message + '</div>';
                }
            })
            .catch(error => {
                console.error('Error fetching files:', error);
                filesContainer.innerHTML = '<div class="error">Error loading files</div>';
            });
    }

    function renderFileList(files) {
        if (!files || files.length === 0) {
            filesContainer.innerHTML = '<div class="empty">No files found</div>';
            return;
        }

        filesContainer.innerHTML = '';
        files.forEach(file => {
            const fileItem = document.createElement('div');
            fileItem.className = 'file-item';
            fileItem.dataset.fileId = file;

            // Add file type icon based on extension
            const extension = file.split('.').pop();
            const icon = getIconForExtension(extension);

            fileItem.innerHTML = `<span class="file-type-icon">${icon}</span> ${file}`;

            fileItem.addEventListener('click', () => selectFile(file));
            filesContainer.appendChild(fileItem);
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
            default: return '📄';
        }
    }

    function selectFile(fileId) {
        // Update UI
        const fileItems = document.querySelectorAll('.file-item');
        fileItems.forEach(item => item.classList.remove('active'));

        const selectedItem = document.querySelector(`.file-item[data-file-id="${fileId}"]`);
        if (selectedItem) {
            selectedItem.classList.add('active');
        }

        // Update state
        currentFileId = fileId;
        currentFileName.textContent = fileId;
    }

    function deleteCurrentFile() {
        if (!currentFileId) return;

        const confirm = window.confirm(`Are you sure you want to delete ${currentFileId}?`);
        if (!confirm) return;

        fetch(`api/files.php?action=delete&id=${currentFileId}`, {
            method: 'DELETE'
        })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    // Reset UI
                    currentFileId = null;
                    currentFileName.textContent = 'No file selected';

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

    function createNewFile() {
        const title = newFileTitle.value.trim();
        const extension = newFileExtension.value.trim();

        if (!title) {
            alert('Please enter a file title');
            return;
        }

        if (!extension) {
            alert('Please enter a file extension');
            return;
        }

        // Create empty content
        const encodedContent = btoa('');

        fetch('api/files.php?action=create', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                title: title,
                extension: extension,
                content: encodedContent
            })
        })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    // Reset form
                    newFileTitle.value = '';
                    newFileExtension.value = '';

                    // Reload file list and select the new file
                    loadFiles(extensionFilter.value || null);

                    // Select the newly created file after a slight delay
                    setTimeout(() => {
                        selectFile(data.id);
                    }, 500);

                    alert('File created successfully');
                } else {
                    alert('Error creating file: ' + data.message);
                }
            })
            .catch(error => {
                console.error('Error creating file:', error);
                alert('Network error when creating file');
            });
    }

    function applyExtensionFilter() {
        const extensions = extensionFilter.value.trim();
        loadFiles(extensions || null);
    }
});