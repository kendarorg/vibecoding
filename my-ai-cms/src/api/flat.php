<?php
// Set the content type to JSON
header('Content-Type: application/json');

// Include the necessary files
require_once '../Settings.php';
require_once '../lib/FlatStorage.php';
require_once '../lib/FlatStorageApi.php';

$requestUri = $_SERVER['REQUEST_URI'];
$index = strpos($requestUri,"/api/flat.php");
$basePath = substr($requestUri,0,$index);


// Create storage instance
$dataDir = Settings::$root.'/content/data';
$structureDir = Settings::$root.'/content/structure';
$storage = new FlatStorage($dataDir, $structureDir);

// Create API instance
$api = new FlatStorageApi($storage);

// Process the request and get the response
$response = $api->processRequest();

// Return the response as JSON
echo json_encode($response);
exit;