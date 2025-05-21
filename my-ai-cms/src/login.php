<?php
// login.php
require_once "lib/Utils.php";
require_once "lib/UserStorage.php";
require_once "Settings.php";

$dataDir = Settings::$root.'/users/data';
$structureDir = Settings::$root.'/users/structure';
$storage = new UserStorage($dataDir, $structureDir);

global  $session;

$currentUser = $session->get('user');
if($currentUser){
    header('Location: index.php');
    exit;
}

$error = '';

// Process login
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $username = $_POST['username'] ?? '';
    $password = $_POST['password'] ?? '';

    // Simple validation
    if (empty($username) || empty($password)) {
        $error = 'Please enter both username and password';
    } else {
        $user = $storage->getUserByUserId(trim($username));
        if($user){
            if(password_verify($password, $user['password'])){
                $session->set('user', $user);
                $session->set('userid', $user['uuid']);
                header('Location: index.php');
                exit;
            }
        }else{
            $error = 'Unauthorized';
        }
    }
}
?>
    <div class="login-container">
        <h2>Login</h2>

        <?php if (!empty($error)): ?>
            <div class="error-message"><?php echo htmlspecialchars($error); ?></div>
        <?php endif; ?>

        <form method="post" action="login.php">
            <div class="form-group">
                <label for="username">Username</label>
                <input type="text" id="username" name="username" required>
            </div>

            <div class="form-group">
                <label for="password">Password</label>
                <input type="password" id="password" name="password" required>
            </div>

            <button type="submit" class="btn btn-primary">Login</button>
        </form>
    </div>