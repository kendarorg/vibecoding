<?php

class FilesStorage {
    private $dataDir;
    private $structureDir;
    private $namesLogFile;

    public function __construct(string $dataDir = 'data',string $indexDir = 'structure') {
        $this->dataDir = $dataDir;
        $this->structureDir = $indexDir;
        $this->namesLogFile = $this->structureDir . '/names.log';

        // Ensure directories exist
        if (!is_dir($this->dataDir)) {
            mkdir($this->dataDir, 0755, true);
        }

        if (!is_dir($this->structureDir)) {
            mkdir($this->structureDir, 0755, true);
        }

        // Ensure names log file exists
        if (!file_exists($this->namesLogFile)) {
            file_put_contents($this->namesLogFile, '');
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

    public function upsertFile($itemId, $itemTitle, $itemContent = null) {
        $filePath = $this->getFilePath($itemId);
        $fileExists = file_exists($filePath);

        // Extract basename for log
        [$basename, $extension] = $this->parseItemId($itemId);

        // If file doesn't exist, create it and log creation
        if (!$fileExists) {
            if ($itemContent !== null) {
                file_put_contents($filePath, $itemContent);
            } else {
                file_put_contents($filePath, '');
            }
            $this->appendToLog('CR', $basename, $itemTitle);
        } else {
            // Check if title has changed
            $currentTitle = $this->getCurrentTitle($basename);
            if ($currentTitle !== $itemTitle) {
                $this->appendToLog('RN', $basename, $itemTitle);
            }

            // Update content if provided
            if ($itemContent !== null) {
                file_put_contents($filePath, $itemContent);
            }
        }

        return true;
    }

    public function deleteFile($itemId) {
        $filePath = $this->getFilePath($itemId);

        if (file_exists($filePath)) {
            // Extract basename for log
            [$basename, $extension] = $this->parseItemId(basename($filePath));

            $this->appendToLog('DE', $basename, '');
            unlink($filePath);
            return true;
        }

        return false;
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
            $itemId = $basename;

            if (!empty($matchingFiles)) {
                $pathInfo = pathinfo($matchingFiles[0]);
                $extension = $pathInfo['extension'] ?? '';
                $itemId = $basename . ($extension ? '.' . $extension : '');
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

        return array_values($activeItems);
    }

    public function listFilesByExtension(...$extensions) {
        $files = $this->listFiles();

        if (empty($extensions)) {
            return $files;
        }

        return array_filter($files, function($file) use ($extensions) {
            return in_array($file['extension'], $extensions);
        });
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