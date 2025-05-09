<?php
// Save as src/files.php

require_once "lib/FilesStorageApi.php";
?>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Files Manager</title>
    <style>
        * {
            box-sizing: border-box;
            font-family: Arial, sans-serif;
        }
        body {
            margin: 0;
            padding: 20px;
            background-color: #f5f5f5;
        }
        .container {
            display: flex;
            max-width: 1200px;
            margin: 0 auto;
            background-color: #fff;
            border-radius: 5px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        .file-list {
            width: 300px;
            border-right: 1px solid #ddd;
            padding: 15px;
            overflow-y: auto;
            max-height: 80vh;
        }
        .file-content {
            flex: 1;
            padding: 15px;
            display: flex;
            flex-direction: column;
        }
        .file-item {
            padding: 8px 12px;
            margin-bottom: 5px;
            cursor: pointer;
            border-radius: 4px;
            transition: background-color 0.2s;
        }
        .file-item:hover {
            background-color: #f0f0f0;
        }
        .file-item.active {
            background-color: #e0e0ff;
        }
        .file-controls {
            display: flex;
            justify-content: space-between;
            margin-bottom: 15px;
        }
        .title-area {
            margin-bottom: 15px;
        }
        .title-input {
            width: 100%;
            padding: 8px;
            font-size: 16px;
            border: 1px solid #ccc;
            border-radius: 4px;
        }
        .editor {
            flex: 1;
            display: flex;
            flex-direction: column;
        }
        textarea {
            flex: 1;
            min-height: 300px;
            padding: 10px;
            font-family: monospace;
            border: 1px solid #ccc;
            border-radius: 4px;
            resize: none;
        }
        button {
            padding: 8px 12px;
            background-color: #4CAF50;
            color: white;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            transition: background-color 0.2s;
        }
        button:hover {
            background-color: #45a049;
        }
        button.delete {
            background-color: #f44336;
        }
        button.delete:hover {
            background-color: #d32f2f;
        }
        .extension-filter {
            margin-bottom: 15px;
        }
        .filter-input {
            width: 100%;
            padding: 8px;
            border: 1px solid #ccc;
            border-radius: 4px;
        }
        .create-file-form {
            margin-top: 20px;
            padding-top: 15px;
            border-top: 1px solid #ddd;
        }
        .form-row {
            margin-bottom: 10px;
        }
        .form-row label {
            display: block;
            margin-bottom: 5px;
        }
        .form-row input {
            width: 100%;
            padding: 8px;
            border: 1px solid #ccc;
            border-radius: 4px;
        }
        .modified {
            background-color: #ff9800;
        }
        .modified:hover {
            background-color: #f57c00;
        }
        .file-type-icon {
            margin-right: 5px;
        }
    </style>
</head>
<body>
<div class="container">
    <div class="file-list">
        <div class="extension-filter">
            <input type="text" id="extensionFilter" class="filter-input" placeholder="Filter by extension (e.g., txt,md,json)">
            <button id="applyFilter">Filter</button>
        </div>

        <div id="files-container">
            <!-- Files will be populated here -->
            <div class="loading">Loading files...</div>
        </div>

        <div class="create-file-form">
            <h3>Create New File</h3>
            <div class="form-row">
                <label for="newFileTitle">File Title</label>
                <input type="text" id="newFileTitle" placeholder="Enter file title">
            </div>
            <div class="form-row">
                <label for="newFileExtension">File Extension</label>
                <input type="text" id="newFileExtension" placeholder="E.g., txt, md, json">
            </div>
            <button id="createFileBtn">Create File</button>
        </div>
    </div>

    <div class="file-content">
        <div class="file-controls">
            <h2 id="currentFileName">No file selected</h2>
            <div>
                <button id="saveBtn">Save</button>
                <button id="deleteBtn" class="delete">Delete</button>
            </div>
        </div>

        <div class="title-area">
            <input type="text" id="titleInput" class="title-input" placeholder="File title" disabled>
        </div>

        <div class="editor">
            <textarea id="contentEditor" placeholder="File content will appear here" disabled></textarea>
        </div>
    </div>
</div>

<script>
    document.addEventListener('DOMContentLoaded', function() {
        // DOM Elements
        const filesContainer = document.getElementById('files-container');
        const contentEditor = document.getElementById('contentEditor');
        const titleInput = document.getElementById('titleInput');
        const currentFileName = document.getElementById('currentFileName');
        const saveBtn = document.getElementById('saveBtn');
        const deleteBtn = document.getElementById('deleteBtn');
        const createFileBtn = document.getElementById('createFileBtn');
        const newFileTitle = document.getElementById('newFileTitle');
        const newFileExtension = document.getElementById('newFileExtension');
        const extensionFilter = document.getElementById('extensionFilter');
        const applyFilter = document.getElementById('applyFilter');

        // State variables
        let currentFileId = null;
        let isModified = false;

        // Initialize
        loadFiles();

        // Event listeners
        saveBtn.addEventListener('click', saveCurrentFile);
        deleteBtn.addEventListener('click', deleteCurrentFile);
        contentEditor.addEventListener('input', markAsModified);
        titleInput.addEventListener('input', markAsModified);
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
            // Check if there are unsaved changes
            if (isModified) {
                const confirm = window.confirm('You have unsaved changes. Do you want to discard them?');
                if (!confirm) return;
            }

            // Update UI
            const fileItems = document.querySelectorAll('.file-item');
            fileItems.forEach(item => item.classList.remove('active'));

            const selectedItem = document.querySelector(`.file-item[data-file-id="${fileId}"]`);
            if (selectedItem) {
                selectedItem.classList.add('active');
            }

            // Reset state
            isModified = false;
            saveBtn.classList.remove('modified');

            // Enable inputs
            contentEditor.disabled = false;
            titleInput.disabled = false;

            // Load file content
            currentFileId = fileId;
            currentFileName.textContent = fileId;

            fetch(`api/files.php?action=content&id=${fileId}`)
                .then(response => response.json())
                .then(data => {
                    if (data.success) {
                        contentEditor.value = data.content || '';

                        // Fetch file title
                        return fetch(`api/files.php?action=metadata&id=${fileId}`);
                    } else {
                        throw new Error(data.message);
                    }
                })
                .then(response => response.json())
                .then(data => {
                    if (data.success) {
                        titleInput.value = data.title || '';
                    }
                })
                .catch(error => {
                    console.error('Error loading file:', error);
                    contentEditor.value = 'Error loading file content';
                });
        }

        function markAsModified() {
            if (!isModified) {
                isModified = true;
                saveBtn.classList.add('modified');
            }
        }

        function saveCurrentFile() {
            if (!currentFileId) return;

            const content = contentEditor.value;
            const title = titleInput.value;

            // Base64 encode the content to ensure binary safety
            const encodedContent = btoa(unescape(encodeURIComponent(content)));

            fetch('api/files.php?action=update', {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    id: currentFileId,
                    title: title,
                    content: encodedContent
                })
            })
                .then(response => response.json())
                .then(data => {
                    if (data.success) {
                        isModified = false;
                        saveBtn.classList.remove('modified');
                        alert('File saved successfully');
                    } else {
                        alert('Error saving file: ' + data.message);
                    }
                })
                .catch(error => {
                    console.error('Error saving file:', error);
                    alert('Network error when saving file');
                });
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
                        contentEditor.value = '';
                        titleInput.value = '';
                        currentFileName.textContent = 'No file selected';
                        contentEditor.disabled = true;
                        titleInput.disabled = true;

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
</script>
</body>
</html>