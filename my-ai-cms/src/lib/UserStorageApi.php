<?php

require_once "UserStorage.php";
require_once "Session.php";

class UserStorageApi {
    private $userStorage;
    private $session;

    public function __construct($dataDir = 'storage') {
        $this->userStorage = new UserStorage($dataDir);
        global $session;
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
}