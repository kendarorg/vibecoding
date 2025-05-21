<?php
// Set the content type to JSON
header('Content-Type: application/json');

// Include the necessary files
require_once '../Settings.php';
require_once '../lib/FlatStorage.php';
require_once '../lib/FlatStorageApi.php';


global $session;
$session->checkLoggedIn();

$requestUri = $_SERVER['REQUEST_URI'];
$index = strpos($requestUri,"/api/flat.php");
$basePath = substr($requestUri,0,$index);


// Create storage instance
$dataDir = Settings::$root.'/'.$session->get('userid').'/content/data';
$structureDir = Settings::$root.'/'.$session->get('userid').'/content/structure';
$storage = new FlatStorage($dataDir, $structureDir);

// Create API instance
$api = new FlatStorageApi($storage);

// Process the request and get the response
$response = $api->processRequest();

// Return the response as JSON
echo json_encode($response);
exit;