document.addEventListener('DOMContentLoaded', function () {
    // DOM elements
    const treePanel = document.getElementById('tree-panel');
    const contextMenu = document.getElementById('context-menu');
    const contentTitle = document.getElementById('content-title');
    const contentEditor = document.getElementById('content-editor');
    const saveButton = document.getElementById('save-button');
    const breadcrumb = document.getElementById('breadcrumb');

    // State variables
    let selectedNode = null;
    let currentItemId = null;
    const breadcrumbPath = [];
    const nodeCache = new Map();

    // Context menu handlers
    const menuRename = document.getElementById('menu-rename');
    const menuDelete = document.getElementById('menu-delete');
    const menuCreate = document.getElementById('menu-create');

    // Load root children
    //loadRootItems();

    // Event handlers
    document.addEventListener('click', function () {
        hideContextMenu();
    });

    contentEditor.addEventListener('input', function () {
        if (currentItemId) {
            // Mark content as modified
            if (!saveButton.classList.contains('modified')) {
                saveButton.classList.add('modified');
                saveButton.textContent = 'Save Content *';
            }
        }
    });

    saveButton.addEventListener('click', function () {
        if (currentItemId) {
            saveContent(currentItemId, contentEditor.value);
        }
    });

    menuRename.addEventListener('click', function () {
        if (contextMenu.dataset.itemId === '00000000-0000-0000-0000-000000000000') {
            alert('Cannot rename root item');
            return;
        }

        const newName = prompt('Enter new name:', contextMenu.dataset.itemTitle);
        if (newName && newName.trim()) {
            renameItem(contextMenu.dataset.itemId, newName.trim());
        }
    });

    menuDelete.addEventListener('click', function () {
        if (contextMenu.dataset.itemId === '00000000-0000-0000-0000-000000000000') {
            alert('Cannot delete root item');
            return;
        }

        if (confirm(`Are you sure you want to delete "${contextMenu.dataset.itemTitle}"?`)) {
            deleteItem(contextMenu.dataset.itemId, contextMenu.dataset.parentId);
        }
    });

    menuCreate.addEventListener('click', function () {
        const newName = prompt('Enter name for new item:');
        if (newName && newName.trim()) {
            createItem(contextMenu.dataset.itemId, newName.trim());
        }
    });


    // Function to create a tree node element
    function createTreeNodeElement(item, parentId) {
        const nodeDiv = document.createElement('div');
        nodeDiv.className = 'tree-node';
        nodeDiv.dataset.id = item.id;
        nodeDiv.dataset.title = item.title;
        if (parentId) nodeDiv.dataset.parentId = parentId;

        // Add to node cache
        nodeCache.set(item.id, {
            element: nodeDiv,
            parentId: parentId,
            title: item.title,
            hasLoadedChildren: false
        });

        // Create label with toggle
        const labelDiv = document.createElement('div');
        labelDiv.className = 'tree-label';

        // Toggle for expanding/collapsing
        const toggleSpan = document.createElement('span');
        toggleSpan.className = 'tree-toggle';
        toggleSpan.textContent = item.hasChildren ? '+' : ' ';
        toggleSpan.addEventListener('click', function (e) {
            e.stopPropagation();
            toggleNode(nodeDiv, item.id);
        });

        // Title span
        const titleSpan = document.createElement('span');
        titleSpan.className = 'tree-title';
        titleSpan.textContent = item.title;

        // Add click handler to select the item
        titleSpan.addEventListener('click', function (e) {
            e.stopPropagation();
            selectNode(nodeDiv, item.id);
        });

        // Add context menu to title
        titleSpan.addEventListener('contextmenu', function (e) {
            e.preventDefault();
            showContextMenu(e.pageX, e.pageY, item.id, item.title, parentId);
        });

        // Assemble label
        labelDiv.appendChild(toggleSpan);
        labelDiv.appendChild(titleSpan);
        nodeDiv.appendChild(labelDiv);

        // Children container
        const childrenDiv = document.createElement('div');
        childrenDiv.className = 'tree-children';
        nodeDiv.appendChild(childrenDiv);

        return nodeDiv;
    }

    // Function to load children for a node
    function loadNodeChildren(parentId, container) {
        // Clear container and add loading indicator
        container.innerHTML = '<div class="loading">Loading...</div>';

        fetchItems(parentId).then(items => {
            container.innerHTML = '';

            if (items.length === 0) {
                const emptyNode = document.createElement('div');
                emptyNode.className = 'empty-node';
                emptyNode.textContent = 'No items';
                container.appendChild(emptyNode);

                // Update toggle
                const toggle = container.parentNode.querySelector('.tree-toggle');
                toggle.textContent = ' ';
            } else {
                items.forEach(item => {
                    const childNode = createTreeNodeElement(item, parentId);
                    container.appendChild(childNode);
                });
            }
        });
    }

    // Function to select a node
    function selectNode(nodeElement, itemId) {
        // Clear previous selection
        if (selectedNode) {
            selectedNode.querySelector('.tree-label').classList.remove('selected');
        }

        // Set new selection
        selectedNode = nodeElement;
        nodeElement.querySelector('.tree-label').classList.add('selected');

        // Load content
        currentItemId = itemId;
        loadContent(itemId);

        // Update breadcrumb
        updateBreadcrumb(itemId);
    }

    // Function to update breadcrumb
    function updateBreadcrumb(itemId) {
        // Build path from node to root
        const path = [];
        let currentId = itemId;

        while (currentId) {
            const node = nodeCache.get(currentId);
            path.unshift({
                id: currentId,
                title: node.title
            });

            // Move to parent
            currentId = node.parentId;

            // Break if we reach root or cycle
            if (currentId === '00000000-0000-0000-0000-000000000000') {
                path.unshift({
                    id: '00000000-0000-0000-0000-000000000000',
                    title: 'Root'
                });
                break;
            }
        }

        // Update breadcrumb
        breadcrumb.innerHTML = '';
        path.forEach((item, index) => {
            const li = document.createElement('li');
            const a = document.createElement('a');
            a.href = '#';
            a.dataset.id = item.id;
            a.textContent = item.title;

            // Add click handler to navigate to this item
            a.addEventListener('click', function (e) {
                e.preventDefault();
                navigateToBreadcrumb(item.id);
            });

            li.appendChild(a);
            breadcrumb.appendChild(li);
        });
    }

    // Function to navigate to an item in the breadcrumb
    function navigateToBreadcrumb(itemId) {
        // Find the node element
        const nodeElement = document.querySelector(`.tree-node[data-id="${itemId}"]`);
        if (nodeElement) {
            // Expand all parent nodes
            let parent = nodeElement.parentNode;
            while (parent && parent.classList.contains('tree-children')) {
                parent.parentNode.classList.add('open');
                const toggle = parent.parentNode.querySelector('.tree-toggle');
                if (toggle) toggle.textContent = '-';
                parent = parent.parentNode.parentNode;
            }

            // Select the node
            selectNode(nodeElement, itemId);
        } else {
            // If node not in DOM, reload from root
            loadRootItems();
            //TODO WHAT TO DO HERE?
            // Note: We'd need more complex logic to automatically navigate to a deeply nested node
        }
    }

    // Function to show context menu
    function showContextMenu(x, y, itemId, itemTitle, parentId) {
        contextMenu.style.left = x + 'px';
        contextMenu.style.top = y + 'px';
        contextMenu.style.display = 'block';
        contextMenu.dataset.itemId = itemId;
        contextMenu.dataset.itemTitle = itemTitle;
        contextMenu.dataset.parentId = parentId;

        // Disable rename and delete for root
        if (itemId === '00000000-0000-0000-0000-000000000000') {
            menuRename.style.color = '#999';
            menuDelete.style.color = '#999';
        } else {
            menuRename.style.color = '';
            menuDelete.style.color = '';
        }
    }

    // Function to hide context menu
    function hideContextMenu() {
        contextMenu.style.display = 'none';
    }

    // API Functions

    // Fetch items from the API
    function fetchItems(parentId) {
        return fetch(`api/flat.php?action=list&parent=${parentId}`)
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    return data.items;
                } else {
                    console.error('API error:', data.message);
                    return [];
                }
            })
            .catch(error => {
                console.error('Network error:', error);
                return [];
            });
    }

    // Load content for an item
    function loadContent(itemId) {
        // Clear editor
        contentEditor.value = '';
        saveButton.classList.remove('modified');
        saveButton.textContent = 'Save Content';

        // Update title
        const node = nodeCache.get(itemId);
        contentTitle.textContent = node ? node.title : 'Loading...';

        // Fetch content
        fetch(`api/flat.php?action=content&id=${itemId}`)
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    contentEditor.value = data.content;
                } else {
                    console.error('API error:', data.message);
                    contentEditor.value = 'Error loading content';
                }
            })
            .catch(error => {
                console.error('Network error:', error);
                contentEditor.value = 'Error loading content';
            });
    }

    // Save content
    function saveContent(itemId, content) {
        const node = nodeCache.get(itemId);

        fetch('api/flat.php?action=update', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                id: itemId,
                content: content
            })
        })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    saveButton.classList.remove('modified');
                    saveButton.textContent = 'Save Content';
                    alert('Content saved successfully');
                } else {
                    console.error('API error:', data.message);
                    alert('Error saving content: ' + data.message);
                }
            })
            .catch(error => {
                console.error('Network error:', error);
                alert('Network error when saving content');
            });
    }

    // Rename item
    function renameItem(itemId, newTitle) {
        fetch('api/flat.php?action=update', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                id: itemId,
                title: newTitle
            })
        })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    // Update node in DOM
                    const nodeElement = document.querySelector(`.tree-node[data-id="${itemId}"]`);
                    if (nodeElement) {
                        const titleSpan = nodeElement.querySelector('.tree-title');
                        titleSpan.textContent = newTitle;
                        nodeElement.dataset.title = newTitle;

                        // Update cache
                        const node = nodeCache.get(itemId);
                        if (node) {
                            node.title = newTitle;
                        }

                        // Update content title if this is the current item
                        if (currentItemId === itemId) {
                            contentTitle.textContent = newTitle;
                            updateBreadcrumb(itemId);
                        }
                    }
                } else {
                    console.error('API error:', data.message);
                    alert('Error renaming item: ' + data.message);
                }
            })
            .catch(error => {
                console.error('Network error:', error);
                alert('Network error when renaming item');
            });
    }

    // Delete item
    function deleteItem(itemId, parentId) {
        fetch(`api/flat.php?action=delete&id=${itemId}&parent=${parentId}`, {
            method: 'GET'
        })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    // Remove node from DOM
                    const nodeElement = document.querySelector(`.tree-node[data-id="${itemId}"]`);
                    if (nodeElement && nodeElement.parentNode) {
                        nodeElement.parentNode.removeChild(nodeElement);
                    }

                    // Clear content if this was the current item
                    if (currentItemId === itemId) {
                        currentItemId = null;
                        contentTitle.textContent = 'No item selected';
                        contentEditor.value = '';
                    }

                    // Remove from cache
                    nodeCache.delete(itemId);
                } else {
                    console.error('API error:', data.message);
                    alert('Error deleting item: ' + data.message);
                }
            })
            .catch(error => {
                console.error('Network error:', error);
                alert('Network error when deleting item');
            });
    }

    // Create item
    function createItem(parentId, title) {
        // Generate UUID for new item
        const itemId = generateUUID();

        fetch('api/flat.php?action=create', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                id: itemId,
                parent: parentId,
                title: title,
                content: ''
            })
        })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    // Find parent node
                    const parentNode = document.querySelector(`.tree-node[data-id="${parentId}"]`);
                    if (parentNode) {
                        // Make sure parent is expanded
                        if (!parentNode.classList.contains('open')) {
                            parentNode.classList.add('open');
                            const toggle = parentNode.querySelector('.tree-toggle');
                            toggle.textContent = '-';
                        }

                        // Update toggle if parent had no children
                        const toggle = parentNode.querySelector('.tree-toggle');
                        if (toggle.textContent === ' ') {
                            toggle.textContent = '-';
                        }

                        // Add child node
                        const childrenContainer = parentNode.querySelector('.tree-children');

                        // Remove "No items" message if present
                        const emptyNode = childrenContainer.querySelector('.empty-node');
                        if (emptyNode) {
                            childrenContainer.removeChild(emptyNode);
                        }

                        // Create new node
                        const newNode = createTreeNodeElement({
                            id: itemId,
                            title: title,
                            hasChildren: false
                        }, parentId);

                        childrenContainer.appendChild(newNode);

                        // Mark parent as having loaded children
                        const parentCachedNode = nodeCache.get(parentId);
                        if (parentCachedNode) {
                            parentCachedNode.hasLoadedChildren = true;
                        }
                    }
                } else {
                    console.error('API error:', data.message);
                    alert('Error creating item: ' + data.message);
                }
            })
            .catch(error => {
                console.error('Network error:', error);
                alert('Network error when creating item');
            });
    }

    // Helper function to generate UUID
    function generateUUID() {
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
            const r = Math.random() * 16 | 0;
            const v = c === 'x' ? r : (r & 0x3 | 0x8);
            return v.toString(16);
        });
    }


    // Function to toggle a node open/closed
    function toggleNode(nodeElement, itemId) {
        const isOpen = nodeElement.classList.contains('open');
        const toggleSpan = nodeElement.querySelector('.tree-toggle');
        const childrenContainer = nodeElement.querySelector('.tree-children');

        if (isOpen) {
            // Close the node
            nodeElement.classList.remove('open');
            toggleSpan.textContent = '+';
            childrenContainer.innerHTML='';
            const cachedNode = nodeCache.get(itemId);
            cachedNode.hasLoadedChildren=false;

            // Remove from session
            removeExpandedNodeFromSession(itemId);
        } else {
            // Open the node
            nodeElement.classList.add('open');
            toggleSpan.textContent = '-';

            // Add to session
            addExpandedNodeToSession(itemId);

            // Load children if not already loaded
            const cachedNode = nodeCache.get(itemId);
            if (!cachedNode.hasLoadedChildren) {
                loadNodeChildren(itemId, childrenContainer);
                cachedNode.hasLoadedChildren = true;
            }
        }

        selectNode(nodeElement, itemId);
    }


    function addExpandedNodeToSession(nodeId) {
        fetch('api/session.php?action=addExpanded', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                nodeId: nodeId
            })
        })
        .catch(error => {
            console.error('Error saving expanded node:', error);
        });
    }

    function removeExpandedNodeFromSession(nodeId) {
        fetch('api/session.php?action=removeExpanded', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                nodeId: nodeId
            })
        })
        .catch(error => {
            console.error('Error removing expanded node:', error);
        });
    }

    // Function to load root items
    function loadRootItems() {

        fetchItems('00000000-0000-0000-0000-000000000000').then(items => {
            // Create root node
            const rootNode = createTreeNodeElement({
                id: '00000000-0000-0000-0000-000000000000',
                title: 'Root',
                hasChildren: items.length > 0
            }, null);

            treePanel.innerHTML = '';
            treePanel.appendChild(rootNode);

            // If items, add them to the root
            if (items.length > 0) {
                const childrenContainer = rootNode.querySelector('.tree-children');
                items.forEach(item => {
                    const childNode = createTreeNodeElement(item, '00000000-0000-0000-0000-000000000000');
                    childrenContainer.appendChild(childNode);
                });
            }
        });
    }




    document.querySelectorAll('.tree-children').forEach(node => {
        node.style.display = 'block';
    });

    document.querySelectorAll('.tree-toggle').forEach(toggleSpan => {
        var nodeDiv = toggleSpan.parentNode.parentNode;
        toggleSpan.addEventListener('click', function (e) {
            e.stopPropagation();
            toggleNode(nodeDiv, nodeDiv.dataset.id);
        });
    });

    document.querySelectorAll('.tree-title').forEach(titleSpan => {
        var nodeDiv = titleSpan.parentNode.parentNode;
        titleSpan.addEventListener('click', function (e) {
            e.stopPropagation();
            selectNode(nodeDiv, nodeDiv.dataset.id);
        });
        titleSpan.addEventListener('contextmenu', function (e) {
            e.preventDefault();
            showContextMenu(e.pageX, e.pageY, nodeDiv.dataset.id, titleSpan.textContent, nodeDiv.dataset.parentId);
        });
    });

    //Initialize nodes that are already in the DOM
    document.querySelectorAll('.tree-node').forEach(node => {
        const id = node.dataset.id;
        const parent = node.dataset.parent;
        const title = node.dataset.title;
        const hasChildren = node.querySelector('.tree-toggle').textContent !== ' ';
        const isOpen = node.classList.contains('open');
        nodeCache.set(id, {
            id: id,
            title: title,
            hasChildren: hasChildren,
            hasLoadedChildren: isOpen && hasChildren,
            parentId:parent
        });
    });
/*
    toggleSpan.className = 'tree-toggle';
    toggleSpan.textContent = item.hasChildren ? '+' : ' ';
    toggleSpan.addEventListener('click', function (e) {
        e.stopPropagation();
        toggleNode(nodeDiv, item.id);
    });

    // Title span
    const titleSpan = document.createElement('span');
    titleSpan.className = 'tree-title';
    titleSpan.textContent = item.title;

    // Add click handler to select the item
    titleSpan.addEventListener('click', function (e) {
        e.stopPropagation();
        selectNode(nodeDiv, item.id);
    });

    // Add context menu to title
    titleSpan.addEventListener('contextmenu', function (e) {
        e.preventDefault();
        showContextMenu(e.pageX, e.pageY, item.id, item.title, parentId);
    });*/

});