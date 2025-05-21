<?php
require_once 'lib/Utils.php';
require_once 'lib/Session.php';

class Settings {
    /**
     * Root directory path where this Settings class is located
     * @var string
     */
    public static $root;
    public static $users;
}

// Initialize the static variable with the directory path of this file
//Settings::$root = dirname(__FILE__);
Settings::$root = dirname(dirname(__FILE__))."/storage";
Settings::$users = [
    'admin' => [
        'password' => password_hash('admin123', PASSWORD_DEFAULT),
        'role' => 'admin'
    ]
];


session_start();
// Initialize session
$session = new Session(Settings::$root);