<?php

require_once "UserStorage.php";
require_once "Session.php";

class UserStorageApi {
    private $userStorage;
    private $session;

    public function __construct($userStorage,$session) {
        $this->userStorage = $userStorage;
        $this->session = $session;
    }

    /**
     * Get all users (requires admin role)
     *
     * @return array Response with users or error
     */
    public function getAllUsers(): array {
        if (!$this->checkAdminPermission()) {
            return ['success' => false, 'error' => 'Permission denied'];
        }

        $users = $this->userStorage->getAllUsers();

        // Remove password hashes from response
        foreach ($users as &$user) {
            unset($user['password']);
        }

        return ['success' => true, 'data' => $users];
    }

    /**
     * Get user by UUID
     *
     * @param string $uuid User UUID
     * @return array Response with user or error
     */
    public function getUserByUuid(string $uuid): array {
        if (!$this->checkAdminPermission() && !$this->isOwnUser($uuid)) {
            return ['success' => false, 'error' => 'Permission denied'];
        }

        $user = $this->userStorage->getUserByUuid($uuid);

        if (!$user) {
            return ['success' => false, 'error' => 'User not found'];
        }

        // Remove password hash from response
        unset($user['password']);

        return ['success' => true, 'data' => $user];
    }

    /**
     * Get users by role (requires admin role)
     *
     * @param string $role Role to filter by
     * @return array Response with users or error
     */
    public function getUsersByRole(string $role): array {
        if (!$this->checkAdminPermission()) {
            return ['success' => false, 'error' => 'Permission denied'];
        }

        $users = $this->userStorage->getUsersByRole($role);

        // Remove password hashes from response
        foreach ($users as &$user) {
            unset($user['password']);
        }

        return ['success' => true, 'data' => $users];
    }

    /**
     * Create a new user (requires admin role)
     *
     * @param string $userId User ID
     * @param string $password Password
     * @param string $role User role
     * @return array Response with UUID or error
     */
    public function createUser(string $userId, string $password, string $role = 'user'): array {
        if (!$this->checkAdminPermission()) {
            return ['success' => false, 'error' => 'Permission denied'];
        }

        try {
            $uuid = $this->userStorage->createUser($userId, $password, $role);
            return ['success' => true, 'data' => ['uuid' => $uuid]];
        } catch (Exception $e) {
            return ['success' => false, 'error' => $e->getMessage()];
        }
    }

    /**
     * Update a user
     *
     * @param string $uuid User UUID
     * @param string|null $password New password (null to keep current)
     * @param string|null $role New role (null to keep current)
     * @return array Response with success status or error
     */
    public function updateUser(string $uuid, ?string $password = null, ?string $role = null): array {
        $currentUser = $this->userStorage->getUserByUuid($uuid);

        if (!$currentUser) {
            return ['success' => false, 'error' => 'User not found'];
        }

        // Regular users can only update their own password, not role
        if (!$this->checkAdminPermission()) {
            if (!$this->isOwnUser($uuid)) {
                return ['success' => false, 'error' => 'Permission denied'];
            }

            // Non-admins can't change roles
            if ($role !== null) {
                return ['success' => false, 'error' => 'Cannot change role without admin permissions'];
            }
        }

        $result = $this->userStorage->updateUser($uuid, $password, $role);

        if ($result) {
            return ['success' => true];
        } else {
            return ['success' => false, 'error' => 'Failed to update user'];
        }
    }

    /**
     * Delete a user (requires admin role)
     *
     * @param string $uuid User UUID
     * @return array Response with success status or error
     */
    public function deleteUser(string $uuid): array {
        if (!$this->checkAdminPermission()) {
            return ['success' => false, 'error' => 'Permission denied'];
        }

        $result = $this->userStorage->deleteUser($uuid);

        if ($result) {
            return ['success' => true];
        } else {
            return ['success' => false, 'error' => 'User not found or could not be deleted'];
        }
    }

    /**
     * Get user history (requires admin role or own user)
     *
     * @param string $identifier UUID or user ID
     * @param bool $isUuid Whether the identifier is a UUID
     * @return array Response with history or error
     */
    public function getUserHistory(string $identifier, bool $isUuid = true): array {
        // If it's a user ID, find the corresponding UUID first
        if (!$isUuid) {
            $user = $this->userStorage->getUserByUserId($identifier);
            if ($user && !$this->checkAdminPermission() && !$this->isOwnUser($user['uuid'])) {
                return ['success' => false, 'error' => 'Permission denied'];
            }
        } else {
            if (!$this->checkAdminPermission() && !$this->isOwnUser($identifier)) {
                return ['success' => false, 'error' => 'Permission denied'];
            }
        }

        $history = $this->userStorage->getUserHistory($identifier, $isUuid);

        return ['success' => true, 'data' => $history];
    }

    /**
     * Check if the current user has admin role
     *
     * @return bool Whether the user is an admin
     */
    private function checkAdminPermission(): bool {
        $currentUser = $this->session->get('user');
        return isset($currentUser['role']) && $currentUser['role'] === 'admin';
    }

    /**
     * Check if the UUID belongs to the current user
     *
     * @param string $uuid User UUID to check
     * @return bool Whether it's the current user
     */
    private function isOwnUser(string $uuid): bool {
        $currentUser = $this->session->get('user');
        return isset($currentUser['uuid']) && $currentUser['uuid'] === $uuid;
    }

    public function processRequest(){
        // Handle request based on method and parameters
        $method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
        $action = $_GET['action'] ?? null;

        $ma = $method.$action;

        // Handle method overrides
        switch($ma){
            case("GETdelete"):
                $method = 'DELETE';
                break;
            case("POSTupdate"):
                $method = 'PUT';
                break;
        }

        try {
            $response = [];

            switch ($method) {
                case 'GET':
                    $this->session->checkLoggedIn();
                    if ($action === 'all') {
                        $response = $this->getAllUsers();
                    } elseif ($action === 'byUuid' && isset($_GET['uuid'])) {
                        $response = $this->getUserByUuid($_GET['uuid']);
                    } elseif ($action === 'byRole' && isset($_GET['role'])) {
                        $response = $this->getUsersByRole($_GET['role']);
                    } elseif ($action === 'history') {
                        $identifier = isset($_GET['uuid']) ? $_GET['uuid'] : (isset($_GET['userId']) ? $_GET['userId'] : null);
                        $isUuid = isset($_GET['uuid']);

                        if ($identifier) {
                            $response = $this->getUserHistory($identifier, $isUuid);
                        } else {
                            http_response_code(400);
                            $response = ['success' => false, 'error' => 'Missing identifier (uuid or userId)'];
                        }
                    } else {
                        http_response_code(400);
                        $response = ['success' => false, 'error' => 'Invalid action or missing parameters'];
                    }
                    break;

                case 'POST':
                    // Get JSON input
                    $json = file_get_contents('php://input');
                    $data = json_decode($json, true);

                    if ($action === 'create') {
                        $this->session->checkLoggedIn();
                        if (!isset($data['userId']) || !isset($data['password'])) {
                            http_response_code(400);
                            $response = ['success' => false, 'error' => 'Missing required fields'];
                            break;
                        }

                        $role = isset($data['role']) ? $data['role'] : 'user';
                        $response = $this->createUser($data['userId'], $data['password'], $role);
                    } else if ($action === 'login' && isset($_GET['login'])) {
                        // Get JSON input
                        $json = file_get_contents('php://input');
                        $data = json_decode($json, true);

                        if (!isset($data['userId']) || !isset($data['password'])) {
                            http_response_code(400);
                            $response = ['success' => false, 'error' => 'Missing userId or password'];
                            break;
                        }

                        $isValid = $this->userStorage->verifyCredentials($data['userId'], $data['password']);

                        if ($isValid) {
                            $user = $this->userStorage->getUserByUserId($data['userId']);
                            // Store user in session but remove password
                            unset($user['password']);
                            $this->session->set('user', $user);
                            $this->session->set('userid', $user['uuid']);
                            $response = ['success' => true, 'data' => $user];
                        } else {
                            http_response_code(401);
                            $response = ['success' => false, 'error' => 'Invalid credentials'];
                        }
                    } else {
                        $this->session->checkLoggedIn();
                        http_response_code(400);
                        $response = ['success' => false, 'error' => 'Invalid action'];
                    }
                    break;

                case 'PUT':
                    $this->session->checkLoggedIn();
                    // Get JSON input
                    $json = file_get_contents('php://input');
                    $data = json_decode($json, true);

                    if ($action === 'update' && isset($_GET['uuid'])) {
                        $password = isset($data['password']) ? $data['password'] : null;
                        $role = isset($data['role']) ? $data['role'] : null;

                        $response = $this->updateUser($_GET['uuid'], $password, $role);
                    } else {
                        http_response_code(400);
                        $response = ['success' => false, 'error' => 'Invalid action or missing UUID'];
                    }
                    break;

                case 'DELETE':
                    $this->session->checkLoggedIn();
                    if ($action === 'delete' && isset($_GET['uuid'])) {
                        $response = $this->deleteUser($_GET['uuid']);
                    } else {
                        http_response_code(400);
                        $response = ['success' => false, 'error' => 'Invalid action or missing UUID'];
                    }
                    break;

                default:
                    $this->session->checkLoggedIn();
                    http_response_code(405);
                    $response = ['success' => false, 'error' => 'Method not allowed'];
            }

            if (isset($response['success']) && $response['success'] === false) {
                http_response_code(400);
            }

            echo json_encode($response);

        } catch (Exception $e) {
            $this->session->checkLoggedIn();
            http_response_code(500);
            echo json_encode(['success' => false, 'error' => 'Server error: ' . $e->getMessage()]);
        }
    }
}