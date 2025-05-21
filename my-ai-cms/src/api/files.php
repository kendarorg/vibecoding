<?php
// Set the content type to JSON
header('Content-Type: application/json');

// Include the necessary files
require_once '../Settings.php';
require_once '../lib/FilesStorage.php';
require_once '../lib/FilesStorageApi.php';


global $session;
$session->checkLoggedIn();

$requestUri = $_SERVER['REQUEST_URI'];
$index = strpos($requestUri,"/api/files.php");
$basePath = substr($requestUri,0,$index);

// Create storage instance
$dataDir = Settings::$root.'/'.$session->get('userid').'/files/data';
$structureDir = Settings::$root.'/'.$session->get('userid').'/files/structure';
$storage = new FilesStorage($dataDir, $structureDir);

// Create API instance
$api = new FilesStorageApi($storage);

// Process the request and get the response
$response = $api->processRequest();
if(!$response) {
    exit;
}
// Return the response as JSON
echo json_encode($response);
exit;