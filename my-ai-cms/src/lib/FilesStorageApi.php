<?php

require_once 'FilesStorage.php';

class FilesStorageApi {
    private FilesStorage $storage;

    public function __construct(FilesStorage $storage) {
        $this->storage = $storage;
    }

    /**
     * Process API requests and return appropriate responses
     *
     * @return array Response data as an associative array
     */
    public function processRequest(): array {
        // Get request method and action
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
            switch ($method) {
                case 'GET':
                    return $this->handleGetRequest($action);
                case 'POST':
                    return $this->handlePostRequest($action);
                case 'PUT':
                    return $this->handlePutRequest($action);
                case 'DELETE':
                    return $this->handleDeleteRequest($action);
                default:
                    throw new InvalidArgumentException('Unsupported HTTP method');
            }
        } catch (Exception $e) {
            return [
                'success' => false,
                'message' => $e->getMessage()
            ];
        }
    }

    public function handleGetRequest(?string $action): array {
        switch ($action) {
            case 'list':
                $extension = $_GET['extension'] ?? null;

                if ($extension) {
                    // If extensions are provided as comma-separated values
                    $extensions = explode(',', $extension);
                    $files = $this->storage->listFilesByExtension(...$extensions);
                } else {
                    $files = $this->storage->listFiles();
                }

                return [
                    'success' => true,
                    'files' => $files
                ];

            case 'content':
                $id = $_GET['id'] ?? null;
                if (!$id) {
                    throw new InvalidArgumentException('Missing file ID');
                }
                $content = $this->storage->getContent($id);

                if ($content === null) {
                    return [
                        'success' => false,
                        'message' => 'File not found'
                    ];
                }

                return [
                    'success' => true,
                    'content' => $content
                ];

            default:
                throw new InvalidArgumentException('Invalid action specified');
        }
    }

    /**
     * Get the raw request body
     *
     * @return string Raw request body
     */
    protected function getRequestBody(): string {
        return file_get_contents('php://input');
    }

    /**
     * Handle POST requests for creating new files
     */
    private function handlePostRequest(?string $action): array {
        if ($action !== 'create') {
            throw new InvalidArgumentException('Invalid action specified');
        }

        // Get request data
        $data = json_decode($this->getRequestBody(), true);
        if (!$data) {
            throw new InvalidArgumentException('Invalid JSON data');
        }

        $fileId = $data['id'] ?? null;
        $title = $data['title'] ?? null;
        $content = $data['content'] ?? '';

        if (!$fileId) {
            throw new InvalidArgumentException('Missing file ID');
        }

        if (!$title) {
            throw new InvalidArgumentException('Missing file title');
        }

        $this->storage->upsertFile($fileId, $title, $content);

        return [
            'success' => true,
            'message' => 'File created successfully',
            'id' => $fileId
        ];
    }

    /**
     * Handle PUT requests for updating existing files
     */
    private function handlePutRequest(?string $action): array {
        if ($action !== 'update') {
            throw new InvalidArgumentException('Invalid action specified');
        }

        // Get request data
        $data = json_decode($this->getRequestBody(), true);
        if (!$data) {
            throw new InvalidArgumentException('Invalid JSON data');
        }

        $fileId = $data['id'] ?? null;
        $title = array_key_exists('title', $data) ? $data['title'] : null;
        $content = array_key_exists('content', $data) ? $data['content'] : null;

        if (!$fileId) {
            throw new InvalidArgumentException('Missing file ID');
        }

        $this->storage->upsertFile($fileId, $title, $content);

        return [
            'success' => true,
            'message' => 'File updated successfully',
            'id' => $fileId
        ];
    }

    /**
     * Handle DELETE requests for removing files
     */
    private function handleDeleteRequest(?string $action): array {
        if ($action !== 'delete') {
            throw new InvalidArgumentException('Invalid action specified');
        }

        $fileId = $_GET['id'] ?? null;

        if (!$fileId) {
            throw new InvalidArgumentException('Missing file ID');
        }

        $result = $this->storage->deleteFile($fileId);

        if (!$result) {
            return [
                'success' => false,
                'message' => 'File not found or could not be deleted'
            ];
        }

        return [
            'success' => true,
            'message' => 'File deleted successfully'
        ];
    }
}

// API endpoint execution if this file is called directly
if (basename($_SERVER['SCRIPT_FILENAME']) === basename(__FILE__)) {
    header('Content-Type: application/json');

    $dataDir = 'storage/files/data';
    $structureDir = 'storage/files/structure';

    $storage = new FilesStorage($dataDir, $structureDir);
    $api = new FilesStorageApi($storage);

    $response = $api->processRequest();
    echo json_encode($response);
    exit;
}