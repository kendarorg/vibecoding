<?php
require_once "lib/Utils.php";
require_once "Settings.php";
?>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>My AI CMS</title>
    <link rel="stylesheet" href="lib/css/style.css">
    <link href="lib/icons/bootstrap-icons.css" rel="stylesheet"/>
</head>
<body>
<?php
global $session;

// Handle page selection via query parameter
if (isset($_GET['p']) && in_array($_GET['p'], ['storage', 'files'])) {
    $newPage = $_GET['p'];
    $currentPage = $session->get('currentPage', 'storage');

    // Only update the session if the page has changed
    if ($newPage !== $currentPage) {
        $session->set('currentPage', $newPage);
    }
}

$currentPage = $session->get('currentPage',"storage");
?>
<div class="top-menu">
    <a href="index.php?p=storage" class="<?php echo $currentPage === 'storage' ? 'active' : ''; ?>">Storage</a>
    <a href="index.php?p=files" class="<?php echo $currentPage === 'files' ? 'active' : ''; ?>">Files</a>
</div>
<?php
//require_once "storage.php";
require_once $currentPage.".php";

?>

<script src="lib/scripts/commons.js"></script>
</body>
</html>
