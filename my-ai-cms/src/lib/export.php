<?php
function buildStyle(){
    ob_start();
    ?>
    * {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
    font-family: Arial, sans-serif;
    }

    .tree-menu {
    width: 300px;
    margin: 20px;
    background-color: #f5f5f5;
    border-radius: 5px;
    box-shadow: 0 2px 5px rgba(0, 0, 0, 0.1);
    }

    .tree-menu ul {
    list-style: none;
    }

    .tree-menu li {
    position: relative;
    border-top: 1px solid #e0e0e0;
    }

    .tree-menu li:first-child {
    border-top: none;
    }

    .tree-menu a {
    display: block;
    padding: 10px 15px;
    text-decoration: none;
    color: #333;
    }

    .tree-menu a:hover {
    background-color: #e9e9e9;
    }

    .tree-menu ul ul {
    display: none;
    background-color: #fff;
    }

    .tree-menu ul ul a {
    padding-left: 30px;
    }

    .tree-menu ul ul ul a {
    padding-left: 45px;
    }

    .tree-menu ul ul ul ul a {
    padding-left: 60px;
    }

    .has-submenu > a::after {
    content: "+";
    position: absolute;
    right: 15px;
    transition: transform 0.3s;
    }

    .has-submenu.open > a::after {
    content: "-";
    }

    .active > a {
    background-color: #4CAF50;
    color: white;
    }
<?php
    return ob_get_clean();
}
function buildJavascript(){
    ob_start();
        ?>
            document.addEventListener('DOMContentLoaded', function() {
                // Add click event to all items with submenus
                const menuItems = document.querySelectorAll('.has-submenu > a');

                menuItems.forEach(item => {
                    item.addEventListener('click', function(e) {
                        e.preventDefault();

                        // Toggle the "open" class on the parent li
                        const parent = this.parentElement;
                        parent.classList.toggle('open');

                        // Toggle submenu visibility
                        const submenu = parent.querySelector('ul');
                        if (submenu.style.display === 'block') {
                            submenu.style.display = 'none';
                        } else {
                            submenu.style.display = 'block';
                        }
                    });
                });

                // Add click event to all menu items without submenus
                const leafItems = document.querySelectorAll('.tree-menu li:not(.has-submenu) > a');

                leafItems.forEach(item => {
                    item.addEventListener('click', function(e) {
                        // Remove 'active' class from all items
                        document.querySelectorAll('.tree-menu li').forEach(li => {
                            li.classList.remove('active');
                        });

                        // Add 'active' class to the clicked item
                        this.parentElement.classList.add('active');
                    });
                });
            });
<?php
    return ob_get_clean();
}