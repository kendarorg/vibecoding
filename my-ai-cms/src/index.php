<?php

require_once "Settings.php";
?>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>My AI CMS</title>
    <link rel="stylesheet" href="lib/css/style.css">
</head>
<body>
<?php
global $session;
$currentPage = $session->get('currentPage',"storage");


//require_once "storage.php";
require_once $currentPage.".php";

?>
</body>
</html>
