<?php

require_once 'lib/FlatStorage.php';
$dataDir = Settings::$root.'/content/data';
$structureDir = Settings::$root.'/content/structure';
$storage = new FlatStorage($dataDir, $structureDir);

// Function to fetch items from the API
function fetchItems($parentId) {
    global $storage;
    $items =$storage->listChildren($parentId);
    // Add additional information about each item
    foreach ($items as &$item) {
        // Check if this item has children
        $hasChildren = $storage->hasChildren($item['id']);
        $item['hasChildren'] = $hasChildren;

        // Add parent information explicitly
        $item['parent'] = $parentId;
    }
    return $items;
}


// Function to render the tree recursively
function renderTree($parentId = '00000000-0000-0000-0000-000000000000', $level = 0) {

    global $session;
    $openNodes = $session->get('openNodes', []);
    $items = fetchItems($parentId);
    $output = '';

    if (empty($items)) {
        return '<div class="empty-node">No items</div>';
    }

    foreach ($items as $item) {
        $id = $item['id'];
        Utils::errorLog("RENDERING ITEM: $id");
        $title = htmlspecialchars($item['title']);
        $hasChildren = $item['hasChildren'] ?? false;

        $isOpen = in_array($id, $openNodes);
        $toggleChar = $hasChildren ? ($isOpen ? '-' : '+') : ' ';
        $openClass = $isOpen ? 'open' : '';

        $indent = str_repeat('    ', $level);
        $output .= "{$indent}<div class=\"tree-node {$openClass}\" data-id=\"{$id}\" data-title=\"{$title}\" data-parent=\"{$parentId}\">\n";
        $output .= "{$indent}    <span class=\"tree-toggle\">{$toggleChar}</span>\n";
        $output .= "{$indent}    <span class=\"tree-title\">{$title}</span>\n";
        $output .= "{$indent}    <div class=\"tree-children\">\n";

        // Recursively render children if the node is open
        if ($isOpen && $hasChildren) {
            $output .= renderTree($id, $level + 1);
        }

        $output .= "{$indent}    </div>\n";
        $output .= "{$indent}</div>\n";
    }

    return $output;
}

// Generate the initial tree structure
$treeHtml = renderTree();
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