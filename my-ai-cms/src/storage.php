<?php

require_once 'lib/FlatStorage.php';
$dataDir = Settings::$root.'/content/data';
$structureDir = Settings::$root.'/content/structure';
$storage = new FlatStorage($dataDir, $structureDir);
global $session;

// Get the list of opened UUIDs from the session
$openedIds = $session->get('opened', []);

// Function to build tree structure recursively
function buildTree($parentUuid, $storage, $openedIds) {
    $children=[];
    if($parentUuid===null){
        $children[] = [
            'id' => '00000000-0000-0000-0000-000000000000',
            'title' => 'Root'
        ];
    }else{
        $children = $storage->listChildren($parentUuid);
    }


    $html = '<div class="tree-children">';

    if (empty($children)) {
        $html .= '<div class="empty-node">No items</div>';
    } else {
        foreach ($children as $child) {
            $isOpen = in_array($child['id'], $openedIds);
            $hasChildren = $storage->hasChildren($child['id']);
            $toggleSymbol = $hasChildren ? ($isOpen ? '-' : '+') : ' ';
            $openClass = $isOpen ? 'open' : '';
            $html .= '<div class="tree-node ' . $openClass . '" data-id="' . htmlspecialchars($child['id']) . '" data-title="' . htmlspecialchars($child['title']).'"';
            if($parentUuid!=null){
                $html.= ' data-parent="' . htmlspecialchars($parentUuid) . '"';
            }
            $html.=">";
            $html .= '<div class="tree-label">';
            $html .= '<span class="tree-toggle">' . $toggleSymbol . '</span>';
            $html .= '<span class="tree-title">' . htmlspecialchars($child['title']) . '</span>';
            $html .= '</div>';


            // If this node has children, recursively build its subtree
            if ($hasChildren) {
                if ($isOpen) {
                    $html .= buildTree($child['id'], $storage, $openedIds);
                } else {
                    $html .= '<div class="tree-children"><div class="empty-node"></div></div>';
                }
            } else {
                $html .= '<div class="tree-children"></div>';
            }

            $html .= '</div>'; // end tree-node
        }
    }

    $html .= '</div>'; // end tree-children
    return $html;
}

// Ensure all parent nodes of opened items are also opened
$expandedIds = [];
foreach ($openedIds as $id) {
    $path = $storage->getFullPath($id);
    foreach ($path as $item) {
        if (!in_array($item['id'], $expandedIds)) {
            $expandedIds[] = $item['id'];
        }
    }
}
$expandedIds = array_unique(array_merge($openedIds, $expandedIds));

// Root node ID
$rootUuid = '00000000-0000-0000-0000-000000000000';

$treeContent = buildTree(null, $storage, $expandedIds);

?>
<link rel="stylesheet" href="lib/css/storage.css">
<link rel="stylesheet" href="lib/css/easymde.min.css">

<div class="header">
    <ul class="breadcrumb" id="breadcrumb">
        <li><a href="#" data-id="00000000-0000-0000-0000-000000000000">Root</a></li>
    </ul>
</div>

<div class="main-container">
    <div class="tree-panel" id="tree-panel">
        <?= $treeContent ?>
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
<script src="lib/scripts/turndown.js"></script>
<script src="lib/scripts/easymde.min.js"></script>
<script src="lib/scripts/storage.js"></script>
