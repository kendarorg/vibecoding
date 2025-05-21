<?php
// Set the content type to JSON
header('Content-Type: application/json');

// Include the necessary files
require_once '../Settings.php';
require_once '../lib/FilesStorage.php';
require_once '../lib/FlatStorage.php';

require_once '../lib/FileExporter.php';

global $session;
$session->checkLoggedIn();

$requestUri = $_SERVER['REQUEST_URI'];
$index = strpos($requestUri,"/api/files.php");
$basePath = substr($requestUri,0,$index);

// Create storage instance
$dataDir = Settings::$root.'/'.$session->get('userid').'/files/data';
$structureDir = Settings::$root.'/'.$session->get('userid').'/files/structure';
$storageFiles = new FilesStorage($dataDir, $structureDir);

// Create storage instance
$dataDir = Settings::$root.'/'.$session->get('userid').'/content/data';
$structureDir = Settings::$root.'/'.$session->get('userid').'/content/structure';
$storageFlat = new FlatStorage($dataDir, $structureDir);


$exporter = new FileExporter($storageFiles, $storageFlat);
$type = 'all'; // Default to 'all' if not specified
if (isset($_GET['type'])) {
    $type = $_GET['type'];
}
$zipPath = $exporter->exportToZip($type);
$exporter->downloadZip($zipPath, 'exported_files.zip');