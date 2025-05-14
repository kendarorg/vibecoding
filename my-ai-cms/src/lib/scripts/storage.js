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

    // Replace the content editor with EasyMDE
    const contentEditorElement = document.getElementById('content-editor');

    // Create a wrapper for the editor
    const editorWrapper = document.createElement('div');
    editorWrapper.id = 'editor-wrapper';
    contentEditorElement.parentNode.insertBefore(editorWrapper, contentEditorElement);
    editorWrapper.appendChild(contentEditorElement);

    // Initialize EasyMDE
    editor = new EasyMDE({
        element: contentEditorElement,
        spellChecker: false,
        autosave: {
            enabled: false
        },
        toolbar: [
            'bold', 'italic', 'heading', '|',
            'quote', 'unordered-list', 'ordered-list', '|',
            'link', 'image', '|',
            'preview', 'side-by-side', 'fullscreen', '|',
            'guide'
        ],
        initialValue: '',
        status: ['lines', 'words'],
        renderingConfig: {
            singleLineBreaks: false,
            codeSyntaxHighlighting: true
        },
        scrollbarStyle: 'native'
    });

    // Start in preview mode
    editor.togglePreview();

    let isReallyChanged = false;

    // Handle the input event for detecting changes
    editor.codemirror.on('change', function() {

        if (currentItemId) {
            if(!isReallyChanged){
                isReallyChanged=true;
                return;
            }
            // Mark content as modified
            if (!saveButton.classList.contains('modified')) {
                saveButton.classList.add('modified');
                saveButton.textContent = 'Save Content *';
            }
        }
    });

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
            showError('Cannot rename root item');
            return;
        }


        // Prompt for new name
        floatingPrompt('Enter new name:'+contextMenu.dataset.itemTitle,
            (result,newName)=>{
            if (result && newName && newName.trim()) {
                renameItem(contextMenu.dataset.itemId, newName.trim());
            }
        });

    });

    menuDelete.addEventListener('click', function () {
        if (contextMenu.dataset.itemId === '00000000-0000-0000-0000-000000000000') {
            showError('Cannot delete root item');
            return;
        }

        floatingConfirm(`Are you sure you want to delete "${contextMenu.dataset.itemTitle}"?`,
            (confirm)=>{
                if(confirm){
                    deleteItem(contextMenu.dataset.itemId, contextMenu.dataset.parentId);
                }
            });
    });

    menuCreate.addEventListener('click', function () {
        floatingPrompt('Enter name for new item:',
            (result,newName)=>{
                if (result && newName && newName.trim()) {
                    createItem(contextMenu.dataset.itemId, newName.trim());
                }
            });
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
        document.querySelector('.content-panel').style.display='flex';
        isReallyChanged=false;
        // Clear editor
        editor.value('');
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
                    isReallyChanged=false;
                    editor.value(data.content);
                } else {
                    console.error('API error:', data.message);
                    editor.value('Error loading content');
                }
            })
            .catch(error => {
                console.error('Network error:', error);
                editor.value('Error loading content');
            });
    }

    // Save content
    function saveContent(itemId, content) {
        const node = nodeCache.get(itemId);
        const editorContent = editor.value();

        fetch('api/flat.php?action=update', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                id: itemId,
                content: editorContent
            })
        })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    saveButton.classList.remove('modified');
                    saveButton.textContent = 'Save Content';
                    showNotification('Content saved successfully');
                } else {
                    console.error('API error:', data.message);
                    showError('Error saving content: ' + data.message);
                }
            })
            .catch(error => {
                console.error('Network error:', error);
                showError('Network error when saving content');
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
                        showNotification("Name changed");
                    }
                } else {
                    console.error('API error:', data.message);
                    showError('Error renaming item: ' + data.message);
                }
            })
            .catch(error => {
                console.error('Network error:', error);
                showError('Network error when renaming item');
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
                    showError('Error deleting item: ' + data.message);
                }
            })
            .catch(error => {
                console.error('Network error:', error);
                showError('Network error when deleting item');
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
                    showError('Error creating item: ' + data.message);
                }
            })
            .catch(error => {
                console.error('Network error:', error);
                showError('Network error when creating item');
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

    let waitPasteCompletion =0;

    editor.codemirror.on("paste", function(cm, e) {
        try {
            // Prevent the default paste action
            e.preventDefault();
            // Show the overlay
            console.log("STARTING PASTE")
            showPasteOverlay();

            const items = [];
            const clipboardData = e.clipboardData || window.clipboardData;

            // Process text separately since it's almost always available
            if (clipboardData.getData('text/plain')) {
                items.push({
                    type: 'text/plain',
                    data: new Blob([clipboardData.getData('text/plain')], {type: 'text/plain'}),
                    url: null
                });
            }

            // If we have HTML content
            if (clipboardData.getData('text/html')) {
                items.push({
                    type: 'text/html',
                    data: new Blob([clipboardData.getData('text/html')], {type: 'text/html'}),
                    url: null
                });
            }

            // Process all available items in clipboard
            if (clipboardData.items) {
                Array.from(clipboardData.items).forEach(item => {
                    const type = item.type;

                    // Skip text items as we've already processed them
                    if (type === 'text/plain' || type === 'text/html') {
                        return;
                    }

                    // Handle files and other blob data
                    if (item.kind === 'file') {
                        const blob = item.getAsFile();
                        if (blob) {
                            // Create object URL for the blob
                            const url = URL.createObjectURL(blob);
                            items.push({
                                type: type,
                                data: blob,
                                url: url
                            });
                        }
                    } else {
                        // For non-file items, try to get as string and convert to blob
                        item.getAsString(str => {
                            if (str) {
                                items.push({
                                    type: type,
                                    data: new Blob([str], {type: type}),
                                    url: null
                                });
                            }
                        });
                    }
                });
            }

            // Process files directly if available
            if (clipboardData.files && clipboardData.files.length > 0) {
                Array.from(clipboardData.files).forEach(file => {
                    const url = URL.createObjectURL(file);
                    items.push({
                        type: file.type,
                        data: file,
                        url: url
                    });
                });
            }

            console.log('Clipboard items:', items);

            // Handle images directly pasted (screenshots, copied images)
            const imageItem = items.find(item => item.type.startsWith('image/'));
            const htmlItem = items.find(item => item.type === 'text/html');
            const textItem = items.find(item => item.type === 'text/plain');
            if (imageItem && imageItem.url) {
                handlePastedImage(imageItem, cm);
            }else if (htmlItem) {
                handlePastedHtml(htmlItem, cm);

            }else if (textItem) {
                waitPasteCompletion=1;
                textItem.data.text().then(text => {
                    const doc = cm.getDoc();
                    const cursor = doc.getCursor();
                    doc.replaceRange(text, cursor);
                    waitPasteCompletion=0;
                });
            }
            let counter = 30;
            waitFor(()=>{
                counter--;
                if(counter===0){
                    showEorr("Error downloading images");
                    hidePasteOverlay();
                    return true;
                }
                console.log("WAITER "+waitPasteCompletion);
                if(waitPasteCompletion===0 ){
                    console.log("ENDING PASTE");
                    hidePasteOverlay();
                    return true;
                }
                return false;
            })
        }catch (e) {
            showError("Error downloading images");
            hidePasteOverlay();
        }
    });

    function waitFor(booleanProducer) {
        setTimeout(() => {
            console.log("Waited for 3 seconds");
            if(!booleanProducer()){
                waitFor(booleanProducer);
            }
        }, 500);

    }

// Function to handle pasted images
    function handlePastedImage(imageItem, cm) {
        const file = imageItem.data;
        const filename = generateImageFilename(file.type.split('/')[1]); // e.g., "pasted-image-12345.png"

        waitPasteCompletion=1;
        // Create FormData for file upload
        const formData = new FormData();
        formData.append('file', file, filename);
        formData.append('id', generateUUID());
        formData.append('title', filename);

        // Upload the image
        fetch('api/files.php?action=upload', {
            method: 'POST',
            body: formData
        })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    // Insert the markdown for the local image
                    const imageMarkdown = `![${filename}](${data.url})`;
                    const doc = cm.getDoc();
                    const cursor = doc.getCursor();
                    doc.replaceRange(imageMarkdown, cursor);
                } else {
                    console.error('Failed to upload image:', data.message);
                    // Fall back to object URL if upload fails
                    const imageMarkdown = `![Image](${imageItem.url})`;
                    const doc = cm.getDoc();
                    const cursor = doc.getCursor();
                    doc.replaceRange(imageMarkdown, cursor);
                }
                console.log("Image uploaded successfully");
                waitPasteCompletion=0;
            })
            .catch(error => {
                console.error('Error uploading image:', error);
                // Fall back to object URL if upload fails
                const imageMarkdown = `![Image](${imageItem.url})`;
                const doc = cm.getDoc();
                const cursor = doc.getCursor();
                doc.replaceRange(imageMarkdown, cursor);
                waitPasteCompletion=0;
            });
    }

// Function to handle pasted HTML with images
    function handlePastedHtml(htmlItem, cm) {
        waitPasteCompletion=1;
        htmlItem.data.text().then(html => {
            // Create a DOM parser to work with the HTML
            const parser = new DOMParser();
            const doc = parser.parseFromString(html, 'text/html');
            const images = doc.querySelectorAll('img');

            // Track promises for all image uploads
            const uploadPromises = [];

            // Process each image in the HTML
            images.forEach((img, index) => {

                const src = img.getAttribute('src');
                if (src && (src.startsWith('data:image/') || src.startsWith('http'))) {
                    const uploadPromise = new Promise((resolve) => {
                        if (src.startsWith('data:image/')) {
                            // Handle data URI images
                            const match = src.match(/^data:image\/([a-zA-Z]+);base64,(.+)$/);
                            if (match) {
                                const imageType = match[1];
                                const base64Data = match[2];
                                const byteString = atob(base64Data);
                                const arrayBuffer = new ArrayBuffer(byteString.length);
                                const intArray = new Uint8Array(arrayBuffer);

                                for (let i = 0; i < byteString.length; i++) {
                                    intArray[i] = byteString.charCodeAt(i);
                                }

                                const blob = new Blob([arrayBuffer], { type: `image/${imageType}` });
                                const filename = generateImageFilename(imageType);

                                // Upload the image
                                const formData = new FormData();
                                formData.append('file', blob, filename);
                                formData.append('id', generateUUID());
                                formData.append('title', filename);

                                fetch('api/files.php?action=upload', {
                                    method: 'POST',
                                    body: formData
                                })
                                    .then(response => response.json())
                                    .then(data => {
                                        if (data.success) {
                                            // Replace the src with the local URL
                                            img.setAttribute('src', data.url);
                                            resolve();
                                        } else {
                                            console.error('Failed to upload embedded image:', data.message);
                                            resolve();
                                        }
                                        console.log("Image uploaded successfully (embedded)");
                                    })
                                    .catch(error => {
                                        console.error('Error uploading embedded image:', error);
                                        resolve();
                                    });
                            } else {
                                resolve();
                            }
                        } else if (src.startsWith('http')) {
                            // Handle remote images
                            // Fetch the image and upload it
                            fetch(src)
                                .then(response => response.blob())
                                .then(blob => {
                                    const extension = src.split('.').pop().split('?')[0] || 'jpg';
                                    const filename = generateImageFilename(extension);

                                    const formData = new FormData();
                                    formData.append('file', blob, filename);
                                    formData.append('id', generateUUID());
                                    formData.append('title', filename);

                                    return fetch('api/files.php?action=upload', {
                                        method: 'POST',
                                        body: formData
                                    });
                                })
                                .then(response => response.json())
                                .then(data => {
                                    if (data.success) {
                                        // Replace the src with the local URL
                                        img.setAttribute('src', data.url);
                                        resolve();
                                    } else {
                                        console.error('Failed to upload remote image:', data.message);
                                        resolve();
                                    }
                                    console.log("Image uploaded successfully (remote) "+src);
                                })
                                .catch(error => {
                                    console.error('Error uploading remote image:', error);
                                    resolve();
                                });
                        } else {
                            resolve();
                        }
                    });

                    uploadPromises.push(uploadPromise);
                }
            });

            // When all images are processed, convert the modified HTML to markdown
            return Promise.all(uploadPromises).then(() => {
                // Get the updated HTML with local image URLs
                const modifiedHtml = doc.body.innerHTML;

                // Convert to markdown
                const turndownService = new TurndownService({
                    headingStyle: 'atx',
                    hr: '---',
                    bulletListMarker: '-',
                    codeBlockStyle: 'fenced',
                    emDelimiter: '*'
                });

                turndownService.keep(['h1', 'h2', 'h3', 'h4', 'h5', 'h6']).filter = function(node) {
                    return node.nodeName.match(/^H[1-6]$/);
                };

                turndownService.keep(['h1', 'h2', 'h3', 'h4', 'h5', 'h6']).replacement = function(content, node) {
                    const level = parseInt(node.tagName.charAt(1));
                    const hashes = '#'.repeat(level);
                    return `\n${hashes} ${content}\n\n`;
                };

                const markdown = turndownService.turndown(modifiedHtml);

                // Insert the markdown at cursor position
                const editor = cm.getDoc();
                const cursor = editor.getCursor();
                editor.replaceRange(markdown, cursor);
                waitPasteCompletion=0;
            });
        });
    }

// Helper function to generate a filename for the pasted image
    function generateImageFilename(extension) {
        const timestamp = new Date().getTime();
        return `pasted-image-${timestamp}.${extension}`;
    }

});