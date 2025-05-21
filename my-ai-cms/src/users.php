<?php
// users.php
require_once "lib/Utils.php";
require_once "Settings.php";

// Check if logged in and is admin
if (!isset($_SESSION['user_id']) || $_SESSION['user_role'] !== 'admin') {
    header('Location: login.php');
    exit;
}

$usersFile = 'config/users.php';
$users = file_exists($usersFile) ? include $usersFile : [];
$message = '';
$error = '';

// Process user actions
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $action = $_POST['action'] ?? '';

    // Add/Edit user
    if ($action === 'save') {
        $username = trim($_POST['username'] ?? '');
        $password = trim($_POST['password'] ?? '');
        $role = $_POST['role'] ?? 'user';
        $userId = $_POST['user_id'] ?? '';

        // Validate input
        if (empty($username)) {
            $error = 'Username is required';
        } elseif ($userId === '' && empty($password)) {
            $error = 'Password is required for new users';
        } elseif ($userId === '' && isset($users[$username])) {
            $error = 'Username already exists';
        } else {
            // Update existing or add new user
            if ($userId !== '' && $userId !== $username && isset($users[$userId])) {
                // Username changed, remove old entry
                $userData = $users[$userId];
                unset($users[$userId]);
                $users[$username] = $userData;
            }

            if (!isset($users[$username])) {
                $users[$username] = ['role' => $role];
            } else {
                $users[$username]['role'] = $role;
            }

            // Update password if provided
            if (!empty($password)) {
                $users[$username]['password'] = password_hash($password, PASSWORD_DEFAULT);
            }

            // Save to file
            if (saveUsers($users, $usersFile)) {
                $message = 'User saved successfully';
            } else {
                $error = 'Failed to save user data';
            }
        }
    }

    // Delete user
    if ($action === 'delete') {
        $userId = $_POST['user_id'] ?? '';

        if (isset($users[$userId])) {
            unset($users[$userId]);

            if (saveUsers($users, $usersFile)) {
                $message = 'User deleted successfully';
            } else {
                $error = 'Failed to delete user';
            }
        } else {
            $error = 'User not found';
        }
    }
}

// Function to save users to file
function saveUsers($users, $filePath) {
    $dir = dirname($filePath);
    if (!file_exists($dir)) {
        mkdir($dir, 0777, true);
    }

    $content = "<?php\nreturn " . var_export($users, true) . ";\n";
    return file_put_contents($filePath, $content) !== false;
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>User Management</title>
    <link rel="stylesheet" href="assets/css/style.css">
</head>
<body>
<div class="container">
    <div class="header">
        <h2>User Management</h2>
        <a href="index.php" class="btn">Back to Main</a>
    </div>

    <?php if (!empty($message)): ?>
        <div class="success-message"><?php echo htmlspecialchars($message); ?></div>
    <?php endif; ?>

    <?php if (!empty($error)): ?>
        <div class="error-message"><?php echo htmlspecialchars($error); ?></div>
    <?php endif; ?>

    <div class="user-form">
        <h3>Add/Edit User</h3>
        <form method="post" action="users.php">
            <input type="hidden" name="action" value="save">
            <input type="hidden" name="user_id" id="user_id" value="">

            <div class="form-group">
                <label for="username">Username</label>
                <input type="text" id="username" name="username" required>
            </div>

            <div class="form-group">
                <label for="password">Password</label>
                <input type="password" id="password" name="password">
                <small>Leave empty to keep existing password when editing</small>
            </div>

            <div class="form-group">
                <label for="role">Role</label>
                <select id="role" name="role">
                    <option value="user">User</option>
                    <option value="admin">Admin</option>
                </select>
            </div>

            <div class="form-buttons">
                <button type="submit" class="btn btn-primary">Save User</button>
                <button type="button" class="btn" onclick="clearForm()">Clear</button>
            </div>
        </form>
    </div>

    <div class="user-list">
        <h3>User List</h3>
        <table>
            <thead>
            <tr>
                <th>Username</th>
                <th>Role</th>
                <th>Actions</th>
            </tr>
            </thead>
            <tbody>
            <?php foreach ($users as $username => $userData): ?>
                <tr>
                    <td><?php echo htmlspecialchars($username); ?></td>
                    <td><?php echo htmlspecialchars($userData['role']); ?></td>
                    <td>
                        <button class="btn btn-small"
                                onclick="editUser('<?php echo htmlspecialchars($username); ?>',
                                        '<?php echo htmlspecialchars($userData['role']); ?>')">Edit</button>

                        <form method="post" action="users.php" style="display:inline">
                            <input type="hidden" name="action" value="delete">
                            <input type="hidden" name="user_id" value="<?php echo htmlspecialchars($username); ?>">
                            <button type="submit" class="btn btn-small btn-danger"
                                    onclick="return confirm('Are you sure you want to delete this user?')">Delete</button>
                        </form>
                    </td>
                </tr>
            <?php endforeach; ?>
            </tbody>
        </table>
    </div>
</div>

<script>
    function editUser(username, role) {
        document.getElementById('user_id').value = username;
        document.getElementById('username').value = username;
        document.getElementById('password').value = '';
        document.getElementById('role').value = role;
    }

    function clearForm() {
        document.getElementById('user_id').value = '';
        document.getElementById('username').value = '';
        document.getElementById('password').value = '';
        document.getElementById('role').value = 'user';
    }
</script>
</body>
</html>