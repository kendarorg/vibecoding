<?php
// users.php
require_once "lib/Utils.php";
require_once "lib/UserStorage.php";
require_once "Settings.php";

global  $session;
$session->checkLoggedIn();

$currentUser = $session->get('user');
$isAdmin = $currentUser['role'] === 'admin';
if(!$isAdmin) {
    http_response_code(401);
    echo "Unauthorized";
    exit;
}

$dataDir = Settings::$root.'/users/data';
$structureDir = Settings::$root.'/users/structure';
$storage = new UserStorage($dataDir, $structureDir);

$message = '';
$error = '';



function  getOrDefault($val,$default=null){
    if($val && trim($val)!==''){
        return trim($val);
    }
    return $default;
}

// Process user actions
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $action = $_POST['action'] ?? '';

    // Add/Edit user
    if ($action === 'save') {
        $username = getOrDefault($_POST['username']);
        $password = getOrDefault($_POST['password']);
        $role = getOrDefault($_POST['role'],'user');
        $userId = getOrDefault($_POST['user_id']);


            // Update existing or add new user
        $createNew = $userId ==null;
        try {
            if ($createNew) {
                $storage->createUser($username, $password, $role);
            } else {
                $storage->updateUser($userId, $username, $password, $role);
            }
            $message = 'User saved successfully';
        }catch (Exception $e){
            $error = 'Failed to save user data: '.$e->getMessage();
        }
    }else if ($action === 'delete') {
        $userId = getOrDefault($_POST['user_id'] );
        $storage->deleteUser($userId);
    }else if ($action === 'impersonate') {
        $userId = getOrDefault($_POST['user_id'] );
        $user = $storage->getUserByUuid($userId);
        $session->set('user', $user);
        $session->set('userid', $user['uuid']);
    }
}

$users = $storage->getAllUsers();
?>

<div class="container">

    <?php if (!empty($message)): ?>
        <div class="success-message"><?php echo htmlspecialchars($message); ?></div>
    <?php endif; ?>

    <?php if (!empty($error)): ?>
        <div class="error-message"><?php echo htmlspecialchars($error); ?></div>
    <?php endif; ?>

    <div class="user-form form-row">
        <h3>Add/Edit User</h3>
        <form method="post" class="" action="index.php?p=users">
            <input type="hidden" name="action" value="save">
            <input type="hidden" name="user_id" id="user_id" value="">

            <div class="form-row">
                <label for="username">Username</label>
                <input type="text" id="username" name="username" required>
            </div>

            <div class="form-row">
                <label for="password">Password</label>
                <input type="password" id="password" name="password">
                <small>Leave empty to keep existing password when editing</small>
            </div>

            <div class="form-row">
                <label for="role">Role</label>
                <select id="role" name="role">
                    <option value="user">User</option>
                    <option value="admin">Admin</option>
                </select>
            </div>

            <div class="form-buttons">
                <button type="submit" class="btn my-button btn-primary">Save User</button>
                <button type="button" class="btn my-button" onclick="clearForm()">Clear</button>
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
                <th colspan="3">Actions</th>
            </tr>
            </thead>
            <tbody>
            <?php foreach ($users as $username => $userData): ?>
                <tr>
                    <td><?php echo htmlspecialchars($userData['userId']); ?></td>
                    <td><?php echo htmlspecialchars($userData['role']); ?></td>
                    <td>
                        <button class="btn btn-small my-button"
                                onclick="editUser('<?php echo $userData['uuid']; ?>','<?php echo htmlspecialchars($username); ?>',
                                        '<?php echo htmlspecialchars($userData['role']); ?>')">Edit</button>

                    </td>
                    <td>
                        <form method="post" action="index.php?p=users" style="display:inline">
                            <input type="hidden" name="action" value="delete">
                            <input type="hidden" name="user_id" value="<?php echo $userData['uuid']; ?>">
                            <button type="submit" class="my-button-danger  btn btn-small btn-danger"
                                    onclick="return confirm('Are you sure you want to delete this user?')">Delete</button>
                        </form>
                    </td>
                    <td>
                        <form method="post" action="index.php?p=users" style="display:inline">
                            <input type="hidden" name="action" value="impersonate">
                            <input type="hidden" name="user_id" value="<?php echo $userData['uuid']; ?>">
                            <button type="submit" class="my-button-danger  btn btn-small btn-danger"
                                    onclick="return confirm('Are you sure you want to impersonate this user?')">Impersonate</button>
                        </form>
                    </td>
                </tr>
            <?php endforeach; ?>
            </tbody>
        </table>
    </div>
</div>

<script>
    function editUser(uuid,username, role) {
        document.getElementById('user_id').value = uuid;
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