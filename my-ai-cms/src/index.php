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
</head>
<body>
<?php
global $session;
$currentPage = "login";

if($session->thisLoggedIn()){

    $currentUser = $session->get('user');
    $isAdmin = $currentUser['role'] === 'admin';
    // Handle page selection via query parameter
    if (isset($_GET['p']) && in_array($_GET['p'], ['storage', 'files','maintenance','users','logout'])) {
        $newPage = $_GET['p'];

        if($newPage === 'logout') {
            $session->destroy();
            header("Location: index.php");
            exit;
        }


        $currentPage = $session->get('currentPage', 'storage');
        // Only update the session if the page has changed
        if ($newPage !== $currentPage) {
            $session->set('currentPage', $newPage);
        }
    }
    $currentPage = $session->get('currentPage', 'storage');




?>
<div class="top-menu">
    <a href="index.php?p=storage" class="<?php echo $currentPage === 'storage' ? 'active' : ''; ?>">Storage</a>
    <a href="index.php?p=files" class="<?php echo $currentPage === 'files' ? 'active' : ''; ?>">Files</a>
    <a href="index.php?p=maintenance" class="<?php echo $currentPage === 'maintenance' ? 'active' : ''; ?>">Maintenance</a>
    <?php if($isAdmin){ ?>
        <a href="index.php?p=users" class="<?php echo $currentPage === 'users' ? 'active' : ''; ?>">Users</a>
    <?php } ?>
    <a href="index.php?p=logout" >Logout</a>
</div>
<?php
}
//require_once "storage.php";
require_once $currentPage.".php";

?>

<script src="lib/scripts/commons.js"></script>
</body>
</html>
