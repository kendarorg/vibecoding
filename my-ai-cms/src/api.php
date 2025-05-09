<?php
// Set the content type to JSON
header('Content-Type: application/json');

// Include the necessary files
require_once 'lib/FlatStorage.php';
require_once 'lib/FlatStorageApi.php';

// Create storage instance
$dataDir = '../storage/data';
$structureDir = '../storage/structure';
$storage = new FlatStorage($dataDir, $structureDir);

// Create API instance
$api = new FlatStorageApi($storage);

// Process the request and get the response
$response = $api->processRequest();

// Return the response as JSON
echo json_encode($response);
exit;