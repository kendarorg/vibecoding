<?php

require_once("Settings.php");
global  $session;
$session->checkLoggedIn();
?>
<link rel="stylesheet" href="lib/css/files.css">
<div class="main-container">
    <div class="file-actions">
        <div class="extension-filter">
            <div class="form-row">
            <input type="text" id="extensionFilter" class="filter-input" placeholder="Filter by extension (e.g., txt,md,json)"/>
            </div>
            <button id="applyFilter">Filter</button>
        </div>

        <div class="file-upload-form">
            <h3>Upload File</h3>
            <div class="form-row">
                <label for="uploadFileTitle">File Title</label>
                <input type="text" id="uploadFileTitle" placeholder="Enter file title">
            </div>
            <div class="form-row">
                <input type="file" id="fileUpload">
            </div>
            <button id="uploadFileBtn">Upload File</button>
        </div>
    </div>
    <div class="file-list">


        <table id="files-table" class="files-table">
            <thead>
            <tr>
                <th>Title</th>
                <th>Preview</th>
                <th>Actions</th>
            </tr>
            </thead>
            <tbody id="files-container">
            <!-- Files will be populated here -->
            <tr>
                <td colspan="3" class="loading">Loading files...</td>
            </tr>
            </tbody>
        </table>
    </div>
</div>

<div id="contextMenu" class="context-menu" style="display: none;">
    <ul>
        <li id="contextRename">Rename</li>
        <li id="contextDelete">Delete</li>
    </ul>
</div>

<script src="lib/scripts/files.js"></script>