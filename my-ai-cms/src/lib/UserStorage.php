<?php

require_once "Utils.php";

class UserStorage {
    private $usersLogFile;
    private $structureDir;
    private $dataDir;

    public function __construct(string $dataDir = 'data',string $indexDir = 'structure') {
        $this->dataDir = $dataDir;
        $this->structureDir = $indexDir;
        $this->usersLogFile = $this->structureDir . '/users.log';


        // Ensure directories exist
        if (!file_exists($this->dataDir)) {
            mkdir($this->dataDir, 0755, true);
        }
        if (!file_exists($this->structureDir)) {
            mkdir($this->structureDir, 0755, true);
        }

        // Ensure the log file exists
        if (!file_exists($this->usersLogFile)) {
            file_put_contents($this->usersLogFile, '');
            $this->createUser("admin", "admin123", "admin");
        }
    }

    /**
     * Create a new user
     *
     * @param string $userId User ID
     * @param string $password Raw password that will be hashed
     * @param string $role User role
     * @return string UUID of the created user
     */
    public function createUser(string $userId, string $password, string $role = 'user'): string {
        // Check if user already exists
        if ($this->getUserByUserId($userId)) {
            throw new Exception("User ID already exists");
        }

        // Generate UUID
        $uuid = Utils::generateUuid();

        // Hash the password
        $hashedPassword = password_hash($password, PASSWORD_DEFAULT);

        // Log the creation
        $this->appendToLog('CR', $uuid, $userId, $hashedPassword, $role);
        file_put_contents($this->dataDir."/$uuid.json", "{}");

        return $uuid;
    }

    /**
     * Update user information
     *
     * @param string $uuid User UUID
     * @param string|null $password New password (null to keep current)
     * @param string|null $role New role (null to keep current)
     * @return bool Success status
     */
    public function updateUser(string $uuid, ?string $username = null, ?string $password = null, ?string $role = null): bool {
        $user = $this->getUserByUuid($uuid);
        if (!$user) {
            return false;
        }

        // Keep existing values if not provided
        $hashedPassword = $password ? password_hash($password, PASSWORD_DEFAULT) : $user['password'];
        $newRole = $role ?: $user['role'];
        $newUsername = $username ?: $user['userId'];

        // Log the update
        $this->appendToLog('UP', $uuid, $newUsername, $hashedPassword, $newRole);

        return true;
    }

    /**
     * Delete a user
     *
     * @param string $uuid User UUID
     * @return bool Success status
     */
    public function deleteUser(string $uuid): bool {
        $user = $this->getUserByUuid($uuid);
        if (!$user) {
            return false;
        }
        if(file_exists($this->dataDir."/$uuid.json")) {
            unlink($this->dataDir."/$uuid.json");
        }

        // Log the deletion
        $this->appendToLog('DE', $uuid, $user['userId'], 'null', 'null');

        return true;
    }

    /**
     * Get all active users
     *
     * @return array List of active users
     */
    public function getAllUsers(): array {
        if (!file_exists($this->usersLogFile)) {
            return [];
        }

        $lines = file($this->usersLogFile, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);
        $activeUsers = [];

        foreach ($lines as $line) {
            $parts = explode(',', $line, 5);
            if (count($parts) < 5) continue;

            list($action, $uuid, $userId, $password, $role) = $parts;

            switch ($action) {
                case 'CR':
                case 'UP':
                    $activeUsers[$uuid] = [
                        'uuid' => $uuid,
                        'userId' => $userId,
                        'password' => $password,
                        'role' => $role
                    ];
                    break;
                case 'DE':
                    unset($activeUsers[$uuid]);
                    break;
            }
        }

        return array_values($activeUsers);
    }

    /**
     * Get a user by UUID
     *
     * @param string $uuid User UUID
     * @return array|null User data or null if not found
     */
    public function getUserByUuid(string $uuid): ?array {
        $users = $this->getAllUsers();

        foreach ($users as $user) {
            if ($user['uuid'] === $uuid) {
                $user['data']= json_decode(file_get_contents($this->dataDir."/$uuid.json"), true);
                return $user;
            }
        }

        return null;
    }

    /**
     * Get a user by user ID
     *
     * @param string $userId User ID
     * @return array|null User data or null if not found
     */
    public function getUserByUserId(string $userId): ?array {
        $users = $this->getAllUsers();

        foreach ($users as $user) {
            if ($user['userId'] === $userId) {
                $uuid = $user['uuid'];
                $user['data']= json_decode(file_get_contents($this->dataDir."/$uuid.json"), true);
                return $user;
            }
        }

        return null;
    }

    /**
     * Get users by role
     *
     * @param string $role Role to search for
     * @return array List of users with the specified role
     */
    public function getUsersByRole(string $role): array {
        $users = $this->getAllUsers();

        return array_filter($users, function($user) use ($role) {
            return $user['role'] === $role;
        });
    }

    /**
     * Get history of operations for a user
     *
     * @param string $identifier UUID or user ID
     * @param bool $isUuid Whether the identifier is a UUID
     * @return array Operation history
     */
    public function getUserHistory(string $identifier, bool $isUuid = true): array {
        if (!file_exists($this->usersLogFile)) {
            return [];
        }

        $lines = file($this->usersLogFile, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);
        $history = [];

        foreach ($lines as $line) {
            $parts = explode(',', $line, 5);
            if (count($parts) < 5) continue;

            list($action, $uuid, $userId, $password, $role) = $parts;

            if (($isUuid && $uuid === $identifier) || (!$isUuid && $userId === $identifier)) {
                $history[] = [
                    'action' => $action,
                    'uuid' => $uuid,
                    'userId' => $userId,
                    'role' => $role,
                    'hasPassword' => $password !== 'null'
                ];
            }
        }

        return $history;
    }

    /**
     * Verify user credentials
     *
     * @param string $userId User ID
     * @param string $password Raw password to verify
     * @return bool Whether credentials are valid
     */
    public function verifyCredentials(string $userId, string $password): bool {
        $user = $this->getUserByUserId($userId);

        if (!$user) {
            return false;
        }

        return password_verify($password, $user['password']);
    }

    /**
     * Append an action to the log file
     *
     * @param string $action Action type (CR, UP, DE)
     * @param string $uuid User UUID
     * @param string $userId User ID
     * @param string $password Hashed password
     * @param string $role User role
     */
    private function appendToLog(string $action, string $uuid, string $userId, string $password, string $role): void {
        $logLine = $action . ',' . $uuid . ',' . $userId . ',' . $password . ',' . $role . PHP_EOL;
        file_put_contents($this->usersLogFile, $logLine, FILE_APPEND);
    }
}