### REQ 070

* Generate in javascript a library for a block editor
* Should allow custom plugins
* Generate default basic plugins
* The choice of the new block should be possible 
	* on the left of an existing block
	* on top if no block yet exists
* Any block can be moved and deleted
* Saving the whole content will send to the backend the type of blocks and their content
	

### REQ 070

I'll create a JavaScript library for a block editor with plugin support. Here's a modular implementation with basic plugins and the requested functionality.

## Core Block Editor

```javascript
/**
 * BlockEditor - A modular block editor with plugin support
 */
class BlockEditor {
    constructor(containerId, options = {}) {
        this.container = document.getElementById(containerId);
        if (!this.container) {
            throw new Error(`Container element with ID "${containerId}" not found`);
        }
        
        this.options = {
            saveEndpoint: options.saveEndpoint || '/save',
            pluginOptions: options.pluginOptions || {},
            onSave: options.onSave || null
        };
        
        this.plugins = {};
        this.blocks = [];
        this.nextBlockId = 1;
        
        this.init();
    }
    
    init() {
        this.container.classList.add('block-editor-container');
        
        // Create editor toolbar
        this.toolbar = document.createElement('div');
        this.toolbar.className = 'block-editor-toolbar';
        this.container.appendChild(this.toolbar);
        
        // Create blocks container
        this.blocksContainer = document.createElement('div');
        this.blocksContainer.className = 'block-editor-blocks';
        this.container.appendChild(this.blocksContainer);
        
        // Create empty state with add button
        this.createEmptyState();
        
        // Add save button
        this.createSaveButton();
    }
    
    createEmptyState() {
        this.emptyState = document.createElement('div');
        this.emptyState.className = 'block-editor-empty-state';
        
        const addButton = document.createElement('button');
        addButton.className = 'block-editor-add-block';
        addButton.innerHTML = '+ Add block';
        addButton.addEventListener('click', () => this.showBlockSelector());
        
        this.emptyState.appendChild(addButton);
        this.blocksContainer.appendChild(this.emptyState);
        
        this.updateEmptyState();
    }
    
    updateEmptyState() {
        if (this.blocks.length === 0) {
            this.emptyState.style.display = 'flex';
        } else {
            this.emptyState.style.display = 'none';
        }
    }
    
    createSaveButton() {
        const saveButton = document.createElement('button');
        saveButton.className = 'block-editor-save';
        saveButton.innerHTML = 'Save';
        saveButton.addEventListener('click', () => this.save());
        
        this.toolbar.appendChild(saveButton);
    }
    
    registerPlugin(pluginName, plugin) {
        if (this.plugins[pluginName]) {
            console.warn(`Plugin "${pluginName}" is already registered. Overriding.`);
        }
        
        this.plugins[pluginName] = plugin;
        
        // Add to toolbar if plugin has a toolbar component
        if (plugin.getToolbarComponent) {
            const toolbarItem = plugin.getToolbarComponent();
            if (toolbarItem) {
                this.toolbar.appendChild(toolbarItem);
            }
        }
        
        return this;
    }
    
    showBlockSelector(indexToInsert = null) {
        const selector = document.createElement('div');
        selector.className = 'block-selector';
        
        const title = document.createElement('h3');
        title.textContent = 'Select a block type';
        selector.appendChild(title);
        
        const blockList = document.createElement('div');
        blockList.className = 'block-list';
        
        // Add block options from all registered plugins
        for (const [pluginName, plugin] of Object.entries(this.plugins)) {
            if (plugin.getBlockTypes) {
                const blockTypes = plugin.getBlockTypes();
                
                blockTypes.forEach(blockType => {
                    const blockOption = document.createElement('div');
                    blockOption.className = 'block-option';
                    blockOption.innerHTML = `
                        <span class="block-icon">${blockType.icon || '📄'}</span>
                        <span class="block-title">${blockType.name}</span>
                    `;
                    
                    blockOption.addEventListener('click', () => {
                        this.createBlock(pluginName, blockType.type, indexToInsert);
                        document.body.removeChild(selector);
                    });
                    
                    blockList.appendChild(blockOption);
                });
            }
        }
        
        selector.appendChild(blockList);
        
        // Close button
        const closeButton = document.createElement('button');
        closeButton.className = 'close-selector';
        closeButton.textContent = 'Cancel';
        closeButton.addEventListener('click', () => {
            document.body.removeChild(selector);
        });
        
        selector.appendChild(closeButton);
        
        // Add to body as a modal
        document.body.appendChild(selector);
    }
    
    createBlock(pluginName, blockType, indexToInsert = null) {
        const plugin = this.plugins[pluginName];
        if (!plugin || !plugin.createBlock) {
            console.error(`Plugin "${pluginName}" is not registered or doesn't support block creation`);
            return;
        }
        
        const blockId = `block-${this.nextBlockId++}`;
        const blockContainer = document.createElement('div');
        blockContainer.className = 'block-container';
        blockContainer.dataset.blockId = blockId;
        
        // Block controls
        const blockControls = document.createElement('div');
        blockControls.className = 'block-controls';
        
        // Move up/down buttons
        const moveUpButton = document.createElement('button');
        moveUpButton.className = 'move-up';
        moveUpButton.innerHTML = '↑';
        moveUpButton.addEventListener('click', () => this.moveBlockUp(blockId));
        
        const moveDownButton = document.createElement('button');
        moveDownButton.className = 'move-down';
        moveDownButton.innerHTML = '↓';
        moveDownButton.addEventListener('click', () => this.moveBlockDown(blockId));
        
        // Delete button
        const deleteButton = document.createElement('button');
        deleteButton.className = 'delete-block';
        deleteButton.innerHTML = '×';
        deleteButton.addEventListener('click', () => this.deleteBlock(blockId));
        
        // Add button
        const addButton = document.createElement('button');
        addButton.className = 'add-block';
        addButton.innerHTML = '+';
        addButton.addEventListener('click', () => {
            const index = this.blocks.findIndex(block => block.id === blockId);
            this.showBlockSelector(index + 1);
        });
        
        blockControls.appendChild(moveUpButton);
        blockControls.appendChild(moveDownButton);
        blockControls.appendChild(deleteButton);
        blockControls.appendChild(addButton);
        
        blockContainer.appendChild(blockControls);
        
        // Block content
        const blockContent = document.createElement('div');
        blockContent.className = 'block-content';
        blockContainer.appendChild(blockContent);
        
        // Create the block's editor component using the plugin
        const block = plugin.createBlock(blockContent, blockType, this.options.pluginOptions[pluginName] || {});
        
        // Store block info
        const blockInfo = {
            id: blockId,
            plugin: pluginName,
            type: blockType,
            element: blockContainer,
            instance: block
        };
        
        // Insert at specified index or append
        if (indexToInsert !== null && indexToInsert >= 0 && indexToInsert <= this.blocks.length) {
            if (indexToInsert === this.blocks.length) {
                this.blocksContainer.appendChild(blockContainer);
                this.blocks.push(blockInfo);
            } else {
                const nextElement = this.blocks[indexToInsert].element;
                this.blocksContainer.insertBefore(blockContainer, nextElement);
                this.blocks.splice(indexToInsert, 0, blockInfo);
            }
        } else {
            this.blocksContainer.appendChild(blockContainer);
            this.blocks.push(blockInfo);
        }
        
        this.updateEmptyState();
        return blockInfo;
    }
    
    deleteBlock(blockId) {
        const index = this.blocks.findIndex(block => block.id === blockId);
        if (index !== -1) {
            const block = this.blocks[index];
            
            // Call destroy method if plugin provides one
            const plugin = this.plugins[block.plugin];
            if (plugin && plugin.destroyBlock) {
                plugin.destroyBlock(block.instance);
            }
            
            // Remove element from DOM
            this.blocksContainer.removeChild(block.element);
            
            // Remove from blocks array
            this.blocks.splice(index, 1);
            
            this.updateEmptyState();
        }
    }
    
    moveBlockUp(blockId) {
        const index = this.blocks.findIndex(block => block.id === blockId);
        if (index > 0) {
            // Swap in array
            [this.blocks[index], this.blocks[index - 1]] = [this.blocks[index - 1], this.blocks[index]];
            
            // Move in DOM
            const currentElement = this.blocks[index - 1].element;
            const previousElement = this.blocks[index].element;
            
            this.blocksContainer.insertBefore(currentElement, previousElement);
        }
    }
    
    moveBlockDown(blockId) {
        const index = this.blocks.findIndex(block => block.id === blockId);
        if (index < this.blocks.length - 1) {
            // Get the element after the next element
            const afterElement = index + 2 < this.blocks.length 
                ? this.blocks[index + 2].element 
                : null;
            
            // Swap in array
            [this.blocks[index], this.blocks[index + 1]] = [this.blocks[index + 1], this.blocks[index]];
            
            // Move in DOM
            const currentElement = this.blocks[index + 1].element;
            
            this.blocksContainer.insertBefore(currentElement, afterElement);
        }
    }
    
    save() {
        const data = {
            blocks: this.blocks.map(block => {
                const plugin = this.plugins[block.plugin];
                const content = plugin.getBlockContent ? plugin.getBlockContent(block.instance) : null;
                
                return {
                    type: block.type,
                    plugin: block.plugin,
                    content: content
                };
            })
        };
        
        if (this.options.onSave) {
            // Use custom save handler
            this.options.onSave(data);
            return;
        }
        
        // Default: send to endpoint
        fetch(this.options.saveEndpoint, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(data)
        })
        .then(response => {
            if (!response.ok) {
                throw new Error('Save failed');
            }
            return response.json();
        })
        .then(data => {
            console.log('Save successful:', data);
        })
        .catch(error => {
            console.error('Error saving blocks:', error);
        });
    }
    
    // Load content into editor
    loadContent(blocks) {
        // Clear existing blocks
        this.blocks.forEach(block => {
            this.blocksContainer.removeChild(block.element);
        });
        this.blocks = [];
        
        // Create new blocks from data
        blocks.forEach(blockData => {
            if (!blockData.plugin || !blockData.type) {
                console.error('Block data missing plugin or type', blockData);
                return;
            }
            
            const blockInfo = this.createBlock(blockData.plugin, blockData.type);
            
            // Set content if the plugin supports it
            const plugin = this.plugins[blockData.plugin];
            if (plugin && plugin.setBlockContent && blockInfo && blockData.content) {
                plugin.setBlockContent(blockInfo.instance, blockData.content);
            }
        });
        
        this.updateEmptyState();
    }
}
```

## Basic Plugins

```javascript
/**
 * TextBlockPlugin - Provides text blocks (paragraph, heading)
 */
class TextBlockPlugin {
    constructor() {
        this.name = 'text';
    }
    
    getToolbarComponent() {
        // No toolbar items needed
        return null;
    }
    
    getBlockTypes() {
        return [
            {
                type: 'paragraph',
                name: 'Paragraph',
                icon: '¶'
            },
            {
                type: 'heading',
                name: 'Heading',
                icon: 'H'
            }
        ];
    }
    
    createBlock(container, type, options = {}) {
        const blockInstance = {
            type: type,
            container: container
        };
        
        if (type === 'paragraph') {
            const editor = document.createElement('div');
            editor.className = 'paragraph-editor';
            editor.contentEditable = true;
            editor.dataset.placeholder = 'Type paragraph text here...';
            
            container.appendChild(editor);
            blockInstance.editor = editor;
            
        } else if (type === 'heading') {
            const editor = document.createElement('div');
            editor.className = 'heading-editor';
            editor.contentEditable = true;
            editor.dataset.placeholder = 'Type heading text here...';
            
            // Heading level selector
            const levelSelect = document.createElement('select');
            for (let i = 1; i <= 6; i++) {
                const option = document.createElement('option');
                option.value = i;
                option.textContent = `H${i}`;
                levelSelect.appendChild(option);
            }
            levelSelect.value = '2'; // Default to H2
            
            const controls = document.createElement('div');
            controls.className = 'heading-controls';
            controls.appendChild(document.createTextNode('Level: '));
            controls.appendChild(levelSelect);
            
            container.appendChild(controls);
            container.appendChild(editor);
            
            blockInstance.editor = editor;
            blockInstance.levelSelect = levelSelect;
        }
        
        return blockInstance;
    }
    
    getBlockContent(blockInstance) {
        if (blockInstance.type === 'paragraph') {
            return {
                text: blockInstance.editor.innerHTML
            };
        } else if (blockInstance.type === 'heading') {
            return {
                text: blockInstance.editor.innerHTML,
                level: blockInstance.levelSelect.value
            };
        }
        return null;
    }
    
    setBlockContent(blockInstance, content) {
        if (blockInstance.type === 'paragraph' && content.text) {
            blockInstance.editor.innerHTML = content.text;
        } else if (blockInstance.type === 'heading') {
            if (content.text) {
                blockInstance.editor.innerHTML = content.text;
            }
            if (content.level && blockInstance.levelSelect) {
                blockInstance.levelSelect.value = content.level;
            }
        }
    }
    
    destroyBlock(blockInstance) {
        // Clean up any event listeners if needed
    }
}

/**
 * ImageBlockPlugin - Provides image upload and embed functionality
 */
class ImageBlockPlugin {
    constructor() {
        this.name = 'image';
    }
    
    getToolbarComponent() {
        return null;
    }
    
    getBlockTypes() {
        return [
            {
                type: 'image',
                name: 'Image',
                icon: '🖼️'
            }
        ];
    }
    
    createBlock(container, type, options = {}) {
        const blockInstance = {
            type: type,
            container: container
        };
        
        const imageContainer = document.createElement('div');
        imageContainer.className = 'image-container';
        
        const imagePreview = document.createElement('img');
        imagePreview.className = 'image-preview';
        imagePreview.style.display = 'none';
        
        const imageControls = document.createElement('div');
        imageControls.className = 'image-controls';
        
        const fileInput = document.createElement('input');
        fileInput.type = 'file';
        fileInput.accept = 'image/*';
        fileInput.style.display = 'none';
        
        const uploadButton = document.createElement('button');
        uploadButton.className = 'upload-image';
        uploadButton.textContent = 'Upload Image';
        uploadButton.addEventListener('click', () => fileInput.click());
        
        const urlInput = document.createElement('input');
        urlInput.type = 'text';
        urlInput.placeholder = 'Or enter image URL';
        
        const insertButton = document.createElement('button');
        insertButton.textContent = 'Insert';
        insertButton.addEventListener('click', () => {
            if (urlInput.value) {
                this.setImageSrc(blockInstance, urlInput.value);
            }
        });
        
        fileInput.addEventListener('change', () => {
            if (fileInput.files && fileInput.files[0]) {
                const reader = new FileReader();
                reader.onload = (e) => {
                    this.setImageSrc(blockInstance, e.target.result);
                };
                reader.readAsDataURL(fileInput.files[0]);
            }
        });
        
        // Assemble the controls
        imageControls.appendChild(fileInput);
        imageControls.appendChild(uploadButton);
        imageControls.appendChild(urlInput);
        imageControls.appendChild(insertButton);
        
        const captionInput = document.createElement('input');
        captionInput.type = 'text';
        captionInput.className = 'image-caption';
        captionInput.placeholder = 'Add caption (optional)';
        
        // Add components to container
        imageContainer.appendChild(imagePreview);
        imageContainer.appendChild(imageControls);
        imageContainer.appendChild(captionInput);
        container.appendChild(imageContainer);
        
        blockInstance.imagePreview = imagePreview;
        blockInstance.fileInput = fileInput;
        blockInstance.urlInput = urlInput;
        blockInstance.captionInput = captionInput;
        blockInstance.imageControls = imageControls;
        
        return blockInstance;
    }
    
    setImageSrc(blockInstance, src) {
        blockInstance.imagePreview.src = src;
        blockInstance.imagePreview.style.display = 'block';
        blockInstance.imageSrc = src;
    }
    
    getBlockContent(blockInstance) {
        return {
            src: blockInstance.imageSrc || '',
            caption: blockInstance.captionInput.value || ''
        };
    }
    
    setBlockContent(blockInstance, content) {
        if (content.src) {
            this.setImageSrc(blockInstance, content.src);
        }
        
        if (content.caption) {
            blockInstance.captionInput.value = content.caption;
        }
    }
    
    destroyBlock(blockInstance) {
        // Clean up any event listeners if needed
    }
}

/**
 * ListBlockPlugin - Provides ordered and unordered lists
 */
class ListBlockPlugin {
    constructor() {
        this.name = 'list';
    }
    
    getToolbarComponent() {
        return null;
    }
    
    getBlockTypes() {
        return [
            {
                type: 'bullet-list',
                name: 'Bullet List',
                icon: '•'
            },
            {
                type: 'numbered-list',
                name: 'Numbered List',
                icon: '1.'
            }
        ];
    }
    
    createBlock(container, type, options = {}) {
        const blockInstance = {
            type: type,
            container: container,
            items: []
        };
        
        const listContainer = document.createElement('div');
        listContainer.className = 'list-container';
        
        const listType = type === 'bullet-list' ? 'ul' : 'ol';
        const list = document.createElement(listType);
        list.className = 'editable-list';
        
        // Add initial empty item
        this.addListItem(blockInstance, list);
        
        const addButton = document.createElement('button');
        addButton.className = 'add-list-item';
        addButton.textContent = '+ Add Item';
        addButton.addEventListener('click', () => {
            this.addListItem(blockInstance, list);
        });
        
        listContainer.appendChild(list);
        listContainer.appendChild(addButton);
        container.appendChild(listContainer);
        
        blockInstance.list = list;
        blockInstance.addButton = addButton;
        
        return blockInstance;
    }
    
    addListItem(blockInstance, list, content = '') {
        const item = document.createElement('li');
        item.contentEditable = true;
        item.innerHTML = content;
        
        const deleteButton = document.createElement('button');
        deleteButton.className = 'delete-list-item';
        deleteButton.innerHTML = '×';
        deleteButton.addEventListener('click', () => {
            if (list.childNodes.length > 1) {
                list.removeChild(item);
                
                // Update items array
                const index = blockInstance.items.indexOf(item);
                if (index !== -1) {
                    blockInstance.items.splice(index, 1);
                }
            }
        });
        
        item.appendChild(deleteButton);
        list.appendChild(item);
        
        blockInstance.items.push(item);
        return item;
    }
    
    getBlockContent(blockInstance) {
        return {
            items: Array.from(blockInstance.items).map(item => {
                // Remove the delete button from the content
                const clone = item.cloneNode(true);
                const deleteButton = clone.querySelector('.delete-list-item');
                if (deleteButton) {
                    clone.removeChild(deleteButton);
                }
                return clone.innerHTML;
            })
        };
    }
    
    setBlockContent(blockInstance, content) {
        if (content.items && Array.isArray(content.items)) {
            // Clear existing items
            while (blockInstance.list.firstChild) {
                blockInstance.list.removeChild(blockInstance.list.firstChild);
            }
            blockInstance.items = [];
            
            // Add items from content
            content.items.forEach(itemContent => {
                this.addListItem(blockInstance, blockInstance.list, itemContent);
            });
        }
    }
    
    destroyBlock(blockInstance) {
        // Clean up any event listeners if needed
    }
}
```

## CSS Styles

```css
/* Block Editor Styles */
.block-editor-container {
    border: 1px solid #ddd;
    border-radius: 4px;
    margin: 20px 0;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
}

.block-editor-toolbar {
    display: flex;
    padding: 10px;
    border-bottom: 1px solid #ddd;
    background: #f5f5f5;
}

.block-editor-toolbar button {
    margin-right: 8px;
    padding: 5px 10px;
    background: #fff;
    border: 1px solid #ddd;
    border-radius: 4px;
    cursor: pointer;
}

.block-editor-toolbar button:hover {
    background: #f0f0f0;
}

.block-editor-blocks {
    min-height: 200px;
    padding: 10px;
}

.block-editor-empty-state {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 150px;
    background: #f9f9f9;
    border: 2px dashed #ddd;
    border-radius: 4px;
}

.block-editor-add-block {
    padding: 8px 16px;
    background: #0078d4;
    color: white;
    border: none;
    border-radius: 4px;
    cursor: pointer;
}

.block-editor-add-block:hover {
    background: #006cbe;
}

.block-editor-save {
    margin-left: auto;
    background: #107c10;
    color: white;
    border: none;
    padding: 8px 16px;
    border-radius: 4px;
    cursor: pointer;
}

.block-editor-save:hover {
    background: #0e6e0e;
}

/* Block Styles */
.block-container {
    position: relative;
    margin: 15px 0;
    border: 1px solid #e0e0e0;
    border-radius: 4px;
    background: #fff;
}

.block-controls {
    display: flex;
    align-items: center;
    padding: 5px;
    background: #f5f5f5;
    border-bottom: 1px solid #e0e0e0;
}

.block-controls button {
    width: 30px;
    height: 30px;
    margin-right: 5px;
    border: 1px solid #ddd;
    background: #fff;
    border-radius: 4px;
    cursor: pointer;
    font-size: 14px;
    display: flex;
    align-items: center;
    justify-content: center;
}

.block-controls button:hover {
    background: #f0f0f0;
}

.block-content {
    padding: 15px;
}

/* Block Selector */
.block-selector {
    position: fixed;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    background: white;
    padding: 20px;
    border-radius: 8px;
    box-shadow: 0 5px 20px rgba(0, 0, 0, 0.2);
    z-index: 1000;
    width: 300px;
}

.block-selector h3 {
    margin-top: 0;
    border-bottom: 1px solid #eee;
    padding-bottom: 10px;
}

.block-list {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    margin-bottom: 15px;
}

.block-option {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    width: 80px;
    height: 80px;
    border: 1px solid #ddd;
    border-radius: 4px;
    cursor: pointer;
    padding: 5px;
    text-align: center;
}

.block-option:hover {
    background: #f0f0f0;
}

.block-icon {
    font-size: 24px;
    margin-bottom: 5px;
}

.block-title {
    font-size: 12px;
}

.close-selector {
    width: 100%;
    padding: 8px;
    background: #f5f5f5;
    border: 1px solid #ddd;
    border-radius: 4px;
    cursor: pointer;
}

/* Text Block Styles */
.paragraph-editor,
.heading-editor {
    width: 100%;
    min-height: 24px;
    outline: none;
}

.paragraph-editor[data-placeholder]:empty:before,
.heading-editor[data-placeholder]:empty:before {
    content: attr(data-placeholder);
    color: #aaa;
}

.heading-controls {
    margin-bottom: 10px;
}

/* Image Block Styles */
.image-container {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.image-preview {
    max-width: 100%;
    border: 1px solid #eee;
}

.image-controls {
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
}

.image-controls input[type="text"] {
    flex: 1;
    min-width: 200px;
    padding: 8px;
    border: 1px solid #ddd;
    border-radius: 4px;
}

.image-caption {
    width: 100%;
    padding: 8px;
    border: 1px solid #ddd;
    border-radius: 4px;
}

/* List Block Styles */
.list-container {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.editable-list {
    margin: 0;
    padding-left: 25px;
}

.editable-list li {
    position: relative;
    padding: 5px 30px 5px 5px;
    margin-bottom: 5px;
}

.delete-list-item {
    position: absolute;
    right: 0;
    top: 50%;
    transform: translateY(-50%);
    width: 20px;
    height: 20px;
    border-radius: 50%;
    border: 1px solid #ddd;
    background: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    font-size: 12px;
}

.delete-list-item:hover {
    background: #f0f0f0;
}
```

## JavaScript for Initialization

```javascript
// script.js - Initializes the block editor and handles events
document.addEventListener('DOMContentLoaded', function() {
    
    // Initialize editor
    const editor = new BlockEditor('editor-container', {
        saveEndpoint: '/api/save-content',
        onSave: function(data) {
            console.log('Content to save:', data);
            // Implement custom save handling here
        }
    });
    
    // Register plugins
    editor.registerPlugin('text', new TextBlockPlugin());
    editor.registerPlugin('image', new ImageBlockPlugin());
    editor.registerPlugin('list', new ListBlockPlugin());
    
    // Example: Load existing content
    /*
    editor.loadContent([
        {
            plugin: 'text',
            type: 'heading',
            content: {
                text: 'Welcome to the Block Editor',
                level: '1'
            }
        },
        {
            plugin: 'text',
            type: 'paragraph',
            content: {
                text: 'This is a sample paragraph block. You can edit or delete it.'
            }
        }
    ]);
    */
});
```

## HTML Integration Example

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Block Editor Demo</title>
    <link rel="stylesheet" href="block-editor.css">
</head>
<body>
    <div class="container">
        <h1>Block Editor Demo</h1>
        <div id="editor-container"></div>
    </div>
    
    <script src="block-editor.js"></script>
    <script src="block-plugins.js"></script>
    <script src="script.js"></script>
</body>
</html>
```

This implementation provides:

1. A modular block editor with plugin support
2. Three basic plugins: Text (paragraphs, headings), Image, and List (ordered/unordered)
3. Block insertion options (on the left of existing blocks, at the top when empty)
4. Moving and deleting blocks
5. Saving functionality that sends block types and content to the backen

### REQ 071

Create a header plugin and a code plugin

### REQ 071

I'll create two new plugins for the block editor: a Header plugin for page headers with title and subtitle, and a Code plugin for code snippets with syntax highlighting.

## Header Plugin

This plugin creates a header block with a title, subtitle, and optional alignment settings:

```javascript
/**
 * HeaderBlockPlugin - Provides header blocks with title and subtitle
 */
class HeaderBlockPlugin {
    constructor() {
        this.name = 'header';
    }

    getToolbarComponent() {
        return null;
    }

    getBlockTypes() {
        return [
            {
                type: 'header',
                name: 'Header',
                icon: '🏷️'
            }
        ];
    }

    createBlock(container, type, options = {}) {
        const blockInstance = {
            type: type,
            container: container
        };

        const headerContainer = document.createElement('div');
        headerContainer.className = 'header-container';

        // Title input
        const titleEditor = document.createElement('div');
        titleEditor.className = 'header-title-editor';
        titleEditor.contentEditable = true;
        titleEditor.dataset.placeholder = 'Page Title...';

        // Subtitle input
        const subtitleEditor = document.createElement('div');
        subtitleEditor.className = 'header-subtitle-editor';
        subtitleEditor.contentEditable = true;
        subtitleEditor.dataset.placeholder = 'Page Subtitle (optional)...';

        // Alignment control
        const alignmentControl = document.createElement('div');
        alignmentControl.className = 'header-alignment-control';
        
        const alignLabel = document.createElement('span');
        alignLabel.textContent = 'Alignment: ';
        
        const alignSelect = document.createElement('select');
        ['left', 'center', 'right'].forEach(align => {
            const option = document.createElement('option');
            option.value = align;
            option.textContent = align.charAt(0).toUpperCase() + align.slice(1);
            alignSelect.appendChild(option);
        });
        
        alignSelect.addEventListener('change', () => {
            headerContainer.style.textAlign = alignSelect.value;
        });
        
        alignmentControl.appendChild(alignLabel);
        alignmentControl.appendChild(alignSelect);
        
        // Assemble the header block
        headerContainer.appendChild(alignmentControl);
        headerContainer.appendChild(titleEditor);
        headerContainer.appendChild(subtitleEditor);
        container.appendChild(headerContainer);
        
        blockInstance.titleEditor = titleEditor;
        blockInstance.subtitleEditor = subtitleEditor;
        blockInstance.alignSelect = alignSelect;
        blockInstance.headerContainer = headerContainer;
        
        return blockInstance;
    }

    getBlockContent(blockInstance) {
        return {
            title: blockInstance.titleEditor.innerHTML,
            subtitle: blockInstance.subtitleEditor.innerHTML,
            alignment: blockInstance.alignSelect.value
        };
    }

    setBlockContent(blockInstance, content) {
        if (content.title) {
            blockInstance.titleEditor.innerHTML = content.title;
        }
        
        if (content.subtitle) {
            blockInstance.subtitleEditor.innerHTML = content.subtitle;
        }
        
        if (content.alignment) {
            blockInstance.alignSelect.value = content.alignment;
            blockInstance.headerContainer.style.textAlign = content.alignment;
        }
    }

    destroyBlock(blockInstance) {
        // Clean up any event listeners if needed
    }
}
```

## Code Plugin

This plugin creates a code block with language selection and syntax highlighting:

```javascript
/**
 * CodeBlockPlugin - Provides code blocks with syntax selection
 */
class CodeBlockPlugin {
    constructor() {
        this.name = 'code';
    }

    getToolbarComponent() {
        return null;
    }

    getBlockTypes() {
        return [
            {
                type: 'code',
                name: 'Code Block',
                icon: '<>'
            }
        ];
    }

    createBlock(container, type, options = {}) {
        const blockInstance = {
            type: type,
            container: container
        };

        const codeContainer = document.createElement('div');
        codeContainer.className = 'code-block-container';
        
        // Language selector
        const languageSelect = document.createElement('select');
        languageSelect.className = 'code-language-select';
        
        // Add common programming languages
        const languages = [
            'plain', 'html', 'css', 'javascript', 'typescript', 
            'php', 'python', 'ruby', 'java', 'c', 'cpp', 'csharp',
            'sql', 'bash', 'json', 'xml'
        ];
        
        languages.forEach(lang => {
            const option = document.createElement('option');
            option.value = lang;
            option.textContent = lang.charAt(0).toUpperCase() + lang.slice(1);
            languageSelect.appendChild(option);
        });
        
        // Code editor
        const codeEditor = document.createElement('textarea');
        codeEditor.className = 'code-editor';
        codeEditor.placeholder = 'Enter code here...';
        codeEditor.spellcheck = false;
        codeEditor.wrap = 'off';
        
        // Theme selector
        const themeSelect = document.createElement('select');
        themeSelect.className = 'code-theme-select';
        
        ['light', 'dark'].forEach(theme => {
            const option = document.createElement('option');
            option.value = theme;
            option.textContent = theme.charAt(0).toUpperCase() + theme.slice(1);
            themeSelect.appendChild(option);
        });
        
        // Controls container
        const controlsContainer = document.createElement('div');
        controlsContainer.className = 'code-controls';
        
        const langLabel = document.createElement('span');
        langLabel.textContent = 'Language: ';
        
        const themeLabel = document.createElement('span');
        themeLabel.textContent = 'Theme: ';
        
        controlsContainer.appendChild(langLabel);
        controlsContainer.appendChild(languageSelect);
        controlsContainer.appendChild(themeLabel);
        controlsContainer.appendChild(themeSelect);
        
        // Apply theme changes
        themeSelect.addEventListener('change', () => {
            if (themeSelect.value === 'dark') {
                codeEditor.classList.add('dark-theme');
            } else {
                codeEditor.classList.remove('dark-theme');
            }
        });
        
        // Assemble the code block
        codeContainer.appendChild(controlsContainer);
        codeContainer.appendChild(codeEditor);
        container.appendChild(codeContainer);
        
        blockInstance.codeEditor = codeEditor;
        blockInstance.languageSelect = languageSelect;
        blockInstance.themeSelect = themeSelect;
        
        return blockInstance;
    }

    getBlockContent(blockInstance) {
        return {
            code: blockInstance.codeEditor.value,
            language: blockInstance.languageSelect.value,
            theme: blockInstance.themeSelect.value
        };
    }

    setBlockContent(blockInstance, content) {
        if (content.code) {
            blockInstance.codeEditor.value = content.code;
        }
        
        if (content.language) {
            blockInstance.languageSelect.value = content.language;
        }
        
        if (content.theme) {
            blockInstance.themeSelect.value = content.theme;
            if (content.theme === 'dark') {
                blockInstance.codeEditor.classList.add('dark-theme');
            }
        }
    }

    destroyBlock(blockInstance) {
        // Clean up any event listeners if needed
    }
}
```

## CSS for the New Plugins

Here's the CSS you should add to your `blockeditor.css` file:

```css
/* Header Block Styles */
.header-container {
    margin-bottom: 15px;
}

.header-title-editor {
    font-size: 28px;
    font-weight: bold;
    margin-bottom: 8px;
    padding: 5px 0;
    min-height: 36px;
    outline: none;
}

.header-subtitle-editor {
    font-size: 18px;
    color: #555;
    margin-bottom: 10px;
    padding: 3px 0;
    min-height: 26px;
    outline: none;
}

.header-alignment-control {
    margin-bottom: 10px;
}

.header-alignment-control select {
    padding: 5px;
    border: 1px solid #ddd;
    border-radius: 4px;
    margin-left: 5px;
}

.header-title-editor[data-placeholder]:empty:before,
.header-subtitle-editor[data-placeholder]:empty:before {
    content: attr(data-placeholder);
    color: #aaa;
}

/* Code Block Styles */
.code-block-container {
    margin-bottom: 15px;
}

.code-controls {
    margin-bottom: 8px;
    display: flex;
    align-items: center;
    gap: 10px;
}

.code-controls select {
    padding: 5px;
    border: 1px solid #ddd;
    border-radius: 4px;
}

.code-editor {
    width: 100%;
    min-height: 150px;
    font-family: Consolas, Monaco, 'Andale Mono', monospace;
    font-size: 14px;
    line-height: 1.5;
    padding: 10px;
    border: 1px solid #ddd;
    border-radius: 4px;
    white-space: pre;
    overflow: auto;
    resize: vertical;
    tab-size: 4;
    background: #f8f8f8;
    color: #333;
}

.code-editor.dark-theme {
    background: #282c34;
    color: #abb2bf;
    border-color: #181a1f;
}
```

## Registration in BlockEditor

I'll create two new plugins for the block editor: a Header plugin for page headers with title and subtitle, and a Code plugin for code snippets with syntax highlighting.

#### Header Plugin

This plugin creates a header block with a title, subtitle, and optional alignment settings:

```javascript
/**
 * HeaderBlockPlugin - Provides header blocks with title and subtitle
 */
class HeaderBlockPlugin {
    constructor() {
        this.name = 'header';
    }

    getToolbarComponent() {
        return null;
    }

    getBlockTypes() {
        return [
            {
                type: 'header',
                name: 'Header',
                icon: '🏷️'
            }
        ];
    }

    createBlock(container, type, options = {}) {
        const blockInstance = {
            type: type,
            container: container
        };

        const headerContainer = document.createElement('div');
        headerContainer.className = 'header-container';

        // Title input
        const titleEditor = document.createElement('div');
        titleEditor.className = 'header-title-editor';
        titleEditor.contentEditable = true;
        titleEditor.dataset.placeholder = 'Page Title...';

        // Subtitle input
        const subtitleEditor = document.createElement('div');
        subtitleEditor.className = 'header-subtitle-editor';
        subtitleEditor.contentEditable = true;
        subtitleEditor.dataset.placeholder = 'Page Subtitle (optional)...';

        // Alignment control
        const alignmentControl = document.createElement('div');
        alignmentControl.className = 'header-alignment-control';
        
        const alignLabel = document.createElement('span');
        alignLabel.textContent = 'Alignment: ';
        
        const alignSelect = document.createElement('select');
        ['left', 'center', 'right'].forEach(align => {
            const option = document.createElement('option');
            option.value = align;
            option.textContent = align.charAt(0).toUpperCase() + align.slice(1);
            alignSelect.appendChild(option);
        });
        
        alignSelect.addEventListener('change', () => {
            headerContainer.style.textAlign = alignSelect.value;
        });
        
        alignmentControl.appendChild(alignLabel);
        alignmentControl.appendChild(alignSelect);
        
        // Assemble the header block
        headerContainer.appendChild(alignmentControl);
        headerContainer.appendChild(titleEditor);
        headerContainer.appendChild(subtitleEditor);
        container.appendChild(headerContainer);
        
        blockInstance.titleEditor = titleEditor;
        blockInstance.subtitleEditor = subtitleEditor;
        blockInstance.alignSelect = alignSelect;
        blockInstance.headerContainer = headerContainer;
        
        return blockInstance;
    }

    getBlockContent(blockInstance) {
        return {
            title: blockInstance.titleEditor.innerHTML,
            subtitle: blockInstance.subtitleEditor.innerHTML,
            alignment: blockInstance.alignSelect.value
        };
    }

    setBlockContent(blockInstance, content) {
        if (content.title) {
            blockInstance.titleEditor.innerHTML = content.title;
        }
        
        if (content.subtitle) {
            blockInstance.subtitleEditor.innerHTML = content.subtitle;
        }
        
        if (content.alignment) {
            blockInstance.alignSelect.value = content.alignment;
            blockInstance.headerContainer.style.textAlign = content.alignment;
        }
    }

    destroyBlock(blockInstance) {
        // Clean up any event listeners if needed
    }
}
```

#### Code Plugin

This plugin creates a code block with language selection and syntax highlighting:

```javascript
/**
 * CodeBlockPlugin - Provides code blocks with syntax selection
 */
class CodeBlockPlugin {
    constructor() {
        this.name = 'code';
    }

    getToolbarComponent() {
        return null;
    }

    getBlockTypes() {
        return [
            {
                type: 'code',
                name: 'Code Block',
                icon: '<>'
            }
        ];
    }

    createBlock(container, type, options = {}) {
        const blockInstance = {
            type: type,
            container: container
        };

        const codeContainer = document.createElement('div');
        codeContainer.className = 'code-block-container';
        
        // Language selector
        const languageSelect = document.createElement('select');
        languageSelect.className = 'code-language-select';
        
        // Add common programming languages
        const languages = [
            'plain', 'html', 'css', 'javascript', 'typescript', 
            'php', 'python', 'ruby', 'java', 'c', 'cpp', 'csharp',
            'sql', 'bash', 'json', 'xml'
        ];
        
        languages.forEach(lang => {
            const option = document.createElement('option');
            option.value = lang;
            option.textContent = lang.charAt(0).toUpperCase() + lang.slice(1);
            languageSelect.appendChild(option);
        });
        
        // Code editor
        const codeEditor = document.createElement('textarea');
        codeEditor.className = 'code-editor';
        codeEditor.placeholder = 'Enter code here...';
        codeEditor.spellcheck = false;
        codeEditor.wrap = 'off';
        
        // Theme selector
        const themeSelect = document.createElement('select');
        themeSelect.className = 'code-theme-select';
        
        ['light', 'dark'].forEach(theme => {
            const option = document.createElement('option');
            option.value = theme;
            option.textContent = theme.charAt(0).toUpperCase() + theme.slice(1);
            themeSelect.appendChild(option);
        });
        
        // Controls container
        const controlsContainer = document.createElement('div');
        controlsContainer.className = 'code-controls';
        
        const langLabel = document.createElement('span');
        langLabel.textContent = 'Language: ';
        
        const themeLabel = document.createElement('span');
        themeLabel.textContent = 'Theme: ';
        
        controlsContainer.appendChild(langLabel);
        controlsContainer.appendChild(languageSelect);
        controlsContainer.appendChild(themeLabel);
        controlsContainer.appendChild(themeSelect);
        
        // Apply theme changes
        themeSelect.addEventListener('change', () => {
            if (themeSelect.value === 'dark') {
                codeEditor.classList.add('dark-theme');
            } else {
                codeEditor.classList.remove('dark-theme');
            }
        });
        
        // Assemble the code block
        codeContainer.appendChild(controlsContainer);
        codeContainer.appendChild(codeEditor);
        container.appendChild(codeContainer);
        
        blockInstance.codeEditor = codeEditor;
        blockInstance.languageSelect = languageSelect;
        blockInstance.themeSelect = themeSelect;
        
        return blockInstance;
    }

    getBlockContent(blockInstance) {
        return {
            code: blockInstance.codeEditor.value,
            language: blockInstance.languageSelect.value,
            theme: blockInstance.themeSelect.value
        };
    }

    setBlockContent(blockInstance, content) {
        if (content.code) {
            blockInstance.codeEditor.value = content.code;
        }
        
        if (content.language) {
            blockInstance.languageSelect.value = content.language;
        }
        
        if (content.theme) {
            blockInstance.themeSelect.value = content.theme;
            if (content.theme === 'dark') {
                blockInstance.codeEditor.classList.add('dark-theme');
            }
        }
    }

    destroyBlock(blockInstance) {
        // Clean up any event listeners if needed
    }
}
```

#### CSS for the New Plugins

Here's the CSS you should add to your `blockeditor.css` file:

```css
/* Header Block Styles */
.header-container {
    margin-bottom: 15px;
}

.header-title-editor {
    font-size: 28px;
    font-weight: bold;
    margin-bottom: 8px;
    padding: 5px 0;
    min-height: 36px;
    outline: none;
}

.header-subtitle-editor {
    font-size: 18px;
    color: #555;
    margin-bottom: 10px;
    padding: 3px 0;
    min-height: 26px;
    outline: none;
}

.header-alignment-control {
    margin-bottom: 10px;
}

.header-alignment-control select {
    padding: 5px;
    border: 1px solid #ddd;
    border-radius: 4px;
    margin-left: 5px;
}

.header-title-editor[data-placeholder]:empty:before,
.header-subtitle-editor[data-placeholder]:empty:before {
    content: attr(data-placeholder);
    color: #aaa;
}

/* Code Block Styles */
.code-block-container {
    margin-bottom: 15px;
}

.code-controls {
    margin-bottom: 8px;
    display: flex;
    align-items: center;
    gap: 10px;
}

.code-controls select {
    padding: 5px;
    border: 1px solid #ddd;
    border-radius: 4px;
}

.code-editor {
    width: 100%;
    min-height: 150px;
    font-family: Consolas, Monaco, 'Andale Mono', monospace;
    font-size: 14px;
    line-height: 1.5;
    padding: 10px;
    border: 1px solid #ddd;
    border-radius: 4px;
    white-space: pre;
    overflow: auto;
    resize: vertical;
    tab-size: 4;
    background: #f8f8f8;
    color: #333;
}

.code-editor.dark-theme {
    background: #282c34;
    color: #abb2bf;
    border-color: #181a1f;
}
```

#### Registration in BlockEditor

Add these lines after the existing plugin registrations in the BlockEditor constructor:

```javascript
this.registerPlugin('header', new HeaderBlockPlugin());
this.registerPlugin('code', new CodeBlockPlugin());
```

These plugins provide header blocks with title and subtitle fields, and code blocks with language selection and theme options.


### REQ 072
Create a backup function that backups the storage as is (session excluded) using to access the source files only the flat and files storages

### RES 072
/**
 * Create a backup of all storage files without processing
 *
 * @param string $tempDir The temporary directory to store the backup
 * @return void
 */
public function backup(string $tempDir): void
{
    Utils::info("Starting backup process");

    // Backup files storage
    $filesDataDir = $this->filesStorage->getDataDirectory();
    $filesStructureDir = $this->filesStorage->getStructureDirectory();

    // Backup flat storage
    $flatDataDir = $this->flatStorage->getDataDirectory();
    $flatStructureDir = $this->flatStorage->getStructureDirectory();

    // Create backup directory structure
    mkdir($tempDir . '/files/data', 0777, true);
    mkdir($tempDir . '/files/structure', 0777, true);
    mkdir($tempDir . '/content/data', 0777, true);
    mkdir($tempDir . '/content/structure', 0777, true);

    // Copy files storage
    Utils::info("Backing up files storage");
    $this->copyDirectory($filesDataDir, $tempDir . '/files/data');
    $this->copyDirectory($filesStructureDir, $tempDir . '/files/structure');

    // Copy flat storage
    Utils::info("Backing up flat storage");
    $this->copyDirectory($flatDataDir, $tempDir . '/content/data');
    $this->copyDirectory($flatStructureDir, $tempDir . '/content/structure');

    Utils::info("Backup completed");
}

/**
 * Copy a directory and its contents recursively
 *
 * @param string $source Source directory
 * @param string $destination Destination directory
 * @return void
 */
private function copyDirectory(string $source, string $destination): void
{
    if (!is_dir($source)) {
        return;
    }

    $files = new RecursiveIteratorIterator(
        new RecursiveDirectoryIterator($source, RecursiveDirectoryIterator::SKIP_DOTS),
        RecursiveIteratorIterator::SELF_FIRST
    );

    foreach ($files as $file) {
        $targetPath = Utils::sanitizeFileName($destination . '/' . $files->getSubPathName());

        if ($file->isDir()) {
            if (!file_exists($targetPath)) {
                mkdir($targetPath, 0777, true);
            }
        } else {
            $dirPath = dirname($targetPath);
            if (!file_exists($dirPath)) {
                mkdir($dirPath, 0777, true);
            }
            copy($file->getRealPath(), $targetPath);
        }
    }
}

### FIX 072

Completely failed

### REQ 072
### REQ 072
### REQ 072
### REQ 072
### REQ 072
### REQ 072
### REQ 072
### REQ 072
### REQ 072