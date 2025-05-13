<?php

require_once 'lib/FlatStorage.php';
$dataDir = Settings::$root.'/content/data';
$structureDir = Settings::$root.'/content/structure';
$storage = new FlatStorage($dataDir, $structureDir);

// Generate the initial tree structure
$treeHtml = [];
?>
<link rel="stylesheet" href="lib/css/storage.css">

<div class="header">
    <ul class="breadcrumb" id="breadcrumb">
        <li><a href="#" data-id="00000000-0000-0000-0000-000000000000">Root</a></li>
    </ul>
</div>

<div class="main-container">
    <div class="tree-panel" id="tree-panel">
        <?php echo $treeHtml; ?>
    </div>

    <div class="content-panel">
        <div class="content-title" id="content-title">No item selected</div>
        <textarea class="content-editor" id="content-editor"></textarea>
        <button class="save-button" id="save-button">Save Content</button>
    </div>
</div>

<div class="context-menu" id="context-menu" style="display: none;">
    <ul>
        <li id="menu-rename">Rename</li>
        <li id="menu-delete">Delete</li>
        <li id="menu-create">Create Child Item</li>
    </ul>
</div>
<script src="lib/scripts/storage.js"></script>