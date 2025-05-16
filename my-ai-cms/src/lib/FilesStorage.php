<?php

require_once "Utils.php";

class FilesStorage {
    private $dataDir;
    private $structureDir;
    private $namesLogFile;
    private $checksumsLogFile;

    public function __construct(string $dataDir = 'data',string $indexDir = 'structure') {
        $this->dataDir = $dataDir;
        $this->structureDir = $indexDir;
        $this->namesLogFile = $this->structureDir . '/names.log';
        $this->checksumsLogFile = $this->structureDir . '/checksums.log';

        // Ensure directories exist
        if (!is_dir($this->dataDir)) {
            mkdir($this->dataDir, 0755, true);
        }

        if (!is_dir($this->structureDir)) {
            mkdir($this->structureDir, 0755, true);
        }

        // Ensure log files exist
        if (!file_exists($this->namesLogFile)) {
            file_put_contents($this->namesLogFile, '');
        }

        if (!file_exists($this->checksumsLogFile)) {
            file_put_contents($this->checksumsLogFile, '');
        }
    }

    /**
     * Parse an itemId to extract its basename and extension
     *
     * @param string $itemId The item ID which may include an extension
     * @return array [$basename, $extension]
     */
    private function parseItemId(string $itemId): array {
        // Extract extension if present in itemId
        $extension = '';
        $basename = $itemId;

        if (strpos($itemId, '.') !== false) {
            $parts = explode('.', $itemId);
            $extension = array_pop($parts);
            $basename = implode('.', $parts);
        }

        return [$basename, $extension];
    }

    /**
     * Get the full file path based on the itemId
     *
     * @param string $itemId The item ID which may or may not include an extension
     * @return string The full file path
     */
    private function getFilePath(string $itemId): string {
        // If the file exists directly with the given ID, return its path
        $directPath = $this->dataDir . '/' . $itemId;
        if (file_exists($directPath)) {
            return $directPath;
        }

        // If itemId doesn't have an extension, try to find a matching file with extension
        if (strpos($itemId, '.') === false) {
            $files = glob($this->dataDir . '/' . $itemId . '.*');
            if (!empty($files)) {
                // Return the first matching file
                return $files[0];
            }
        }

        // Default: return path as is (for new files or when no match is found)
        return $directPath;
    }

    /**
     * Check if a file with the given MD5 checksum exists and return its UUID
     *
     * @param string $filemd5 The MD5 checksum to check
     * @return string|null The UUID of the file if found, null otherwise
     */
    private function getUuidByChecksum($filemd5) {
        if (!file_exists($this->checksumsLogFile)) {
            return null;
        }

        $lines = file($this->checksumsLogFile, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);
        $uuidByChecksum = [];

        foreach ($lines as $line) {
            $parts = explode(',', $line, 3);
            if (count($parts) < 3) continue;

            list($action, $uuid, $checksum) = $parts;

            if ($action === 'CR' || $action === 'UP') {
                $uuidByChecksum[$checksum] = $uuid;
            } elseif ($action === 'DE' && isset($uuidByChecksum[$checksum])) {
                unset($uuidByChecksum[$checksum]);
            }
        }

        return isset($uuidByChecksum[$filemd5]) ? $uuidByChecksum[$filemd5] : null;
    }

    /**
     * Log checksum information
     *
     * @param string $action The action (CR, UP, DE)
     * @param string $uuid The UUID of the file
     * @param string $filemd5 The MD5 checksum of the file
     */
    private function logChecksum($action, $uuid, $filemd5) {
        $logLine = $action . ',' . $uuid . ',' . $filemd5 . PHP_EOL;
        file_put_contents($this->checksumsLogFile, $logLine, FILE_APPEND);
    }

    public function upsertFile($itemId, $itemTitle, $itemContent = null) {
        $filePath = $this->getFilePath($itemId);
        $fileExists = file_exists($filePath);

        // Extract basename for log
        [$basename, $extension] = $this->parseItemId($itemId);

        // If file doesn't exist, create it and log creation
        if (!$fileExists) {
            if ($itemContent !== null) {
                // Check if we already have a file with this checksum
                $filemd5 = md5($itemContent);
                $existingUuid = $this->getUuidByChecksum($filemd5);

                Utils::errorLog("Existing UUID: " . $existingUuid." with md5: " . $filemd5);


                if ($existingUuid !== null) {
                    // Return the existing UUID instead of creating a new file
                    return $existingUuid;
                }

                file_put_contents($filePath, $itemContent);
                $this->appendToLog('CR', $basename, $itemTitle);
                $this->logChecksum('CR', $basename, $filemd5);
            } else {
                file_put_contents($filePath, '');
                $this->appendToLog('CR', $basename, $itemTitle);
                $this->logChecksum('CR', $basename, md5(''));
            }
        } else {
            // Check if title has changed
            $currentTitle = $this->getCurrentTitle($basename);
            if ($currentTitle !== $itemTitle) {
                $this->appendToLog('RN', $basename, $itemTitle);
            }

            // Update content if provided
            if ($itemContent !== null) {
                file_put_contents($filePath, $itemContent);
                // Log the updated checksum
                $filemd5 = md5($itemContent);
                $this->logChecksum('UP', $basename, $filemd5);
            }
        }

        return $itemId;
    }

    public function deleteFile($itemId) {
        $filePath = $this->getFilePath($itemId);

        if (file_exists($filePath)) {
            // Extract basename for log
            [$basename, $extension] = $this->parseItemId(basename($filePath));

            // Get file checksum before deleting
            $filemd5 = md5(file_get_contents($filePath));

            $this->appendToLog('DE', $basename, '');
            $this->logChecksum('DE', $basename, $filemd5);

            unlink($filePath);
        }

        return true;
    }

    public function listFiles() {
        if (!file_exists($this->namesLogFile)) {
            return [];
        }

        $lines = file($this->namesLogFile, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);
        $activeItems = [];

        foreach ($lines as $line) {
            $parts = explode(',', $line, 3);
            if (count($parts) < 3) continue;

            list($action, $basename, $itemTitle) = $parts;

            // Find matching file with extension
            $matchingFiles = glob($this->dataDir . '/' . $basename . '.*');
            $extension = '';

            if (!empty($matchingFiles)) {
                $pathInfo = pathinfo($matchingFiles[0]);
                $extension = $pathInfo['extension'] ?? '';
            }

            switch ($action) {
                case 'CR':
                    $activeItems[$basename] = [
                        'id' => $basename,
                        'title' => $itemTitle,
                        'basename' => $basename,
                        'extension' => $extension
                    ];
                    break;
                case 'RN':
                    if (isset($activeItems[$basename])) {
                        $activeItems[$basename]['title'] = $itemTitle;
                    }
                    break;
                case 'DE':
                    unset($activeItems[$basename]);
                    break;
            }
        }
        $result = [];
        foreach($activeItems as $key => $item){
            $result[] = $item;
        }
        return $result;
    }

    public function listFilesByExtension($extensions) {
        $files = $this->listFiles();
        if(is_string($extensions)){
            $extensions = explode(",",$extensions);
        }

        if (empty($extensions)) {
            $result = [];
            foreach($files as $key => $item){
                $result[] = $item;
            }
            return $result;
        }

        $files =  array_filter($files, function($file) use ($extensions) {
            return in_array($file['extension'], $extensions);
        });
        $result = [];
        foreach($files as $key => $item){
            $result[] = $item;
        }
        return $result;
    }

    public function getContent($itemId) {
        $filePath = $this->getFilePath($itemId);

        if (file_exists($filePath)) {
            return file_get_contents($filePath);
        }

        return null;
    }

    // Helper methods
    private function appendToLog($action, $basename, $itemTitle) {
        $logLine = $action . ',' . $basename . ',' . $itemTitle . PHP_EOL;
        file_put_contents($this->namesLogFile, $logLine, FILE_APPEND);
    }

    private function getCurrentTitle($basename) {
        if (!file_exists($this->namesLogFile)) {
            return null;
        }

        $lines = file($this->namesLogFile, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);
        $title = null;

        foreach ($lines as $line) {
            $parts = explode(',', $line, 3);
            if (count($parts) < 3) continue;

            list($action, $id, $itemTitle) = $parts;

            if ($id === $basename) {
                if ($action === 'CR' || $action === 'RN') {
                    $title = $itemTitle;
                } else if ($action === 'DE') {
                    $title = null;
                }
            }
        }

        return $title;
    }
}