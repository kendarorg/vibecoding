
<?php

class Settings {
    /**
     * Root directory path where this Settings class is located
     * @var string
     */
    public static $root;
}

// Initialize the static variable with the directory path of this file
//Settings::$root = dirname(__FILE__);
Settings::$root = dirname(dirname(__FILE__))."/storage";