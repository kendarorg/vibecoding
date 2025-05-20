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
        this.registerPlugin('text', new TextBlockPlugin());
        this.registerPlugin('image', new ImageBlockPlugin());
        this.registerPlugin('list', new ListBlockPlugin());
        this.registerPlugin('header', new HeaderBlockPlugin());
        this.registerPlugin('code', new CodeBlockPlugin());
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