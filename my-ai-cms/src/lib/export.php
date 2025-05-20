<?php
function buildStyle(){
    ob_start();
    ?>
        body {
            font-family: Arial, sans-serif;
            margin: 0;
            padding: 0;
            display: flex;
            height: 100vh;
            overflow: hidden;
        }

        #sidebar {
            width: 300px;
            height: 100%;
            overflow: auto;
            background-color: #f5f5f5;
            padding: 10px;
            box-sizing: border-box;
            border-right: 1px solid #ddd;
        }

        #content {
            flex: 1;
            height: 100%;
            overflow: auto;
            padding: 10px;
            box-sizing: border-box;
        }

        .tree-menu ul {
            list-style-type: none;
            padding-left: 20px;
        }

        .tree-menu {
            padding-left: 0;
        }

        .tree-menu li {
            margin: 5px 0;
        }

        .folder {
            cursor: pointer;
            user-select: none;
            font-weight: bold;
        }

        .folder::before {
            content: "▶";
            display: inline-block;
            margin-right: 5px;
            font-size: 10px;
            transition: transform 0.2s;
        }

        .folder.open::before {
            transform: rotate(90deg);
        }

        .folder-content {
            display: none;
        }

        .folder.open + .folder-content {
            display: block;
        }

        .file {
            cursor: pointer;
            padding: 2px 0;
        }

        .file:hover {
            text-decoration: underline;
            color: #007bff;
        }

        iframe {
            width: 100%;
            height: 100%;
            border: none;
        }
<?php
    return ob_get_clean();
}
function buildJavascript(){
    ob_start();
        ?>
        document.addEventListener('DOMContentLoaded', function() {
            // Handle folder clicks
            document.querySelectorAll('.folder').forEach(folder => {
                folder.addEventListener('click', function(e) {
                    e.stopPropagation();
                    this.classList.toggle('open');
                    let path = this.getAttribute('data-path');
                    document.getElementById('content-frame').src = path;

                    // Highlight selected file
                    document.querySelectorAll('.file.selected').forEach(selected => {
                    selected.classList.remove('selected');
                    });
                    this.classList.add('selected');
                });
            });

            // Handle file clicks
            document.querySelectorAll('.file').forEach(file => {
                file.addEventListener('click', function(e) {
                    e.stopPropagation();
                    let path = this.getAttribute('data-path');
                    document.getElementById('content-frame').src = path;

                    // Highlight selected file
                    document.querySelectorAll('.file.selected').forEach(selected => {
                        selected.classList.remove('selected');
                    });
                    this.classList.add('selected');
                });
            });

            // Expand all folders function
            window.expandAll = function() {
                document.querySelectorAll('.folder').forEach(folder => {
                    folder.classList.add('open');
                });
            };

            // Collapse all folders function
            window.collapseAll = function() {
                document.querySelectorAll('.folder').forEach(folder => {
                    folder.classList.remove('open');
                });
            };
        });
<?php
    return ob_get_clean();
}