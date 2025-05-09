<link rel="stylesheet" href="lib/css/files.css">
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

    <div class="file-actions">
        <div class="file-controls">
            <h2 id="currentFileName">No file selected</h2>
            <div>
                <button id="deleteBtn" class="delete">Delete</button>
            </div>
        </div>
    </div>
</div>

<script src="lib/scripts/files.js"></script>