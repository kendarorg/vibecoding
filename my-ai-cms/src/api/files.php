<?php
// Set the content type to JSON
header('Content-Type: application/json');

// Include the necessary files
require_once '../Settings.php';
require_once '../lib/FilesStorage.php';
require_once '../lib/FilesStorageApi.php';

// Create storage instance
$dataDir = Settings::$root.'/files/data';
$structureDir = Settings::$root.'/files/structure';
$storage = new FilesStorage($dataDir, $structureDir);

// Create API instance
$api = new FilesStorageApi($storage);

// Process the request and get the response
$response = $api->processRequest();

// Return the response as JSON
echo json_encode($response);
exit;