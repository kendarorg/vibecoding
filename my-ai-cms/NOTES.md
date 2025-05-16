Customized toolbar for EasyMDE (Markdown Editor)

```javascript1
toolbar: [
            {
                name: "bold",
                action: EasyMDE.toggleBold,
                className: "bi bi-type-bold",
                title: "Bold"
            },
            {
                name: "italic",
                action: EasyMDE.toggleItalic,
                className: "fa fa-italic",
                title: "Italic"
            },
            {
                name: "heading",
                action: EasyMDE.toggleHeadingSmaller,
                className: "fa fa-header",
                title: "Smaller Heading"
            },
            {
                name: "quote",
                action: EasyMDE.toggleBlockquote,
                className: "fa fa-quote-left",
                title: "Quote"
            },
            {
                name: "unordered-list",
                action: EasyMDE.toggleUnorderedList,
                className: "fa fa-list-ul",
                title: "Generic List"
            },
            {
                name: "ordered-list",
                action: EasyMDE.toggleOrderedList,
                className: "fa fa-list-ol",
                title: "Numbered List"
            },
            "|",
            {
                name: "link",
                action: EasyMDE.drawLink,
                className: "fa fa-link",
                title: "Create Link"
            },
            {
                name: "image",
                action: EasyMDE.drawImage,
                className: "fa fa-picture-o",
                title: "Insert Image"
            },
            "|",
            {
                name: "preview",
                action: EasyMDE.togglePreview,
                className: "fa fa-eye no-disable",
                title: "Toggle Preview"
            },
            {
                name: "side-by-side",
                action: EasyMDE.toggleSideBySide,
                className: "fa fa-columns no-disable no-mobile",
                title: "Toggle Side by Side"
            },
            {
                name: "fullscreen",
                action: EasyMDE.toggleFullScreen,
                className: "fa fa-arrows-alt no-disable no-mobile",
                title: "Toggle Fullscreen"
            }
        ],
```