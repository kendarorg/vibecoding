<?php
// Set the content type to JSON
header('Content-Type: application/json');

// Include the necessary files
require_once '../Settings.php';
require_once '../lib/UserStorage.php';
require_once '../lib/UserStorageApi.php';

global $session;

$requestUri = $_SERVER['REQUEST_URI'];
$index = strpos($requestUri,"/api/flat.php");
$basePath = substr($requestUri,0,$index);


// Create storage instance
$dataDir = Settings::$root.'/users/data';
$structureDir = Settings::$root.'/users/structure';
$storage = new UserStorage($dataDir, $structureDir);

// Create API instance
$api = new UserStorageApi($storage,$session);

// Process the request and get the response
$response = $api->processRequest();

// Return the response as JSON
echo json_encode($response);
exit;