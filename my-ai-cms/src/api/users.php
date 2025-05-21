<?php
require_once '../Settings.php';
require_once "../lib/UserStorage.php";
require_once "../lib/UserStorageApi.php";

// Create API instance
$userApi = new UserStorageApi(Settings::$root);

// Set content type to JSON
header('Content-Type: application/json');

// Handle request based on method and parameters
$method = $_SERVER['REQUEST_METHOD'];
$action = isset($_GET['action']) ? $_GET['action'] : null;

try {
    $response = [];

    // Check if user is logged in for most operations
    if ($method !== 'OPTIONS' && !isset($_GET['login'])) {
        if (!$session->has('user')) {
            http_response_code(401);
            echo json_encode(['success' => false, 'error' => 'Authentication required']);
            exit;
        }
    }

    switch ($method) {
        case 'GET':
            if ($action === 'all') {
                $response = $userApi->getAllUsers();
            } elseif ($action === 'byUuid' && isset($_GET['uuid'])) {
                $response = $userApi->getUserByUuid($_GET['uuid']);
            } elseif ($action === 'byRole' && isset($_GET['role'])) {
                $response = $userApi->getUsersByRole($_GET['role']);
            } elseif ($action === 'history') {
                $identifier = isset($_GET['uuid']) ? $_GET['uuid'] : (isset($_GET['userId']) ? $_GET['userId'] : null);
                $isUuid = isset($_GET['uuid']);

                if ($identifier) {
                    $response = $userApi->getUserHistory($identifier, $isUuid);
                } else {
                    http_response_code(400);
                    $response = ['success' => false, 'error' => 'Missing identifier (uuid or userId)'];
                }
            } elseif ($action === 'login' && isset($_GET['login'])) {
                // Get JSON input
                $json = file_get_contents('php://input');
                $data = json_decode($json, true);

                if (!isset($data['userId']) || !isset($data['password'])) {
                    http_response_code(400);
                    $response = ['success' => false, 'error' => 'Missing userId or password'];
                    break;
                }

                $userStorage = new UserStorage(Settings::$root);
                $isValid = $userStorage->verifyCredentials($data['userId'], $data['password']);

                if ($isValid) {
                    $user = $userStorage->getUserByUserId($data['userId']);
                    // Store user in session but remove password
                    unset($user['password']);
                    $session->set('user', $user);
                    $response = ['success' => true, 'data' => $user];
                } else {
                    http_response_code(401);
                    $response = ['success' => false, 'error' => 'Invalid credentials'];
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
                if (!isset($data['userId']) || !isset($data['password'])) {
                    http_response_code(400);
                    $response = ['success' => false, 'error' => 'Missing required fields'];
                    break;
                }

                $role = isset($data['role']) ? $data['role'] : 'user';
                $response = $userApi->createUser($data['userId'], $data['password'], $role);
            } else {
                http_response_code(400);
                $response = ['success' => false, 'error' => 'Invalid action'];
            }
            break;

        case 'PUT':
            // Get JSON input
            $json = file_get_contents('php://input');
            $data = json_decode($json, true);

            if ($action === 'update' && isset($_GET['uuid'])) {
                $password = isset($data['password']) ? $data['password'] : null;
                $role = isset($data['role']) ? $data['role'] : null;

                $response = $userApi->updateUser($_GET['uuid'], $password, $role);
            } else {
                http_response_code(400);
                $response = ['success' => false, 'error' => 'Invalid action or missing UUID'];
            }
            break;

        case 'DELETE':
            if ($action === 'delete' && isset($_GET['uuid'])) {
                $response = $userApi->deleteUser($_GET['uuid']);
            } else {
                http_response_code(400);
                $response = ['success' => false, 'error' => 'Invalid action or missing UUID'];
            }
            break;

        default:
            http_response_code(405);
            $response = ['success' => false, 'error' => 'Method not allowed'];
    }

    if (isset($response['success']) && $response['success'] === false) {
        http_response_code(400);
    }

    echo json_encode($response);

} catch (Exception $e) {
    http_response_code(500);
    echo json_encode(['success' => false, 'error' => 'Server error: ' . $e->getMessage()]);
}