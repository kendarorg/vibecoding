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

    public function upsertFile($itemId, $itemTitle, $itemContent = null) {
        // Extract extension if present in itemId
        $extension = '';
        if (strpos($itemId, '.') !== false) {
            list($itemId, $extension) = explode('.', $itemId, 2);
        }

        $filePath = $this->dataDir . '/' . $itemId;
        if ($extension) {
            $filePath .= '.' . $extension;
        }

        $fileExists = file_exists($filePath);

        // If file doesn't exist, create it and log creation
        if (!$fileExists) {
            if ($itemContent !== null) {
                file_put_contents($filePath, $itemContent);
            }
            $this->appendToLog('CR', $itemId . ($extension ? '.' . $extension : ''), $itemTitle);
        } else {
            // Check if title has changed
            $currentTitle = $this->getCurrentTitle($itemId . ($extension ? '.' . $extension : ''));
            if ($currentTitle !== $itemTitle) {
                $this->appendToLog('RN', $itemId . ($extension ? '.' . $extension : ''), $itemTitle);
            }

            // Update content if provided
            if ($itemContent !== null) {
                file_put_contents($filePath, $itemContent);
            }
        }

        return true;
    }

    public function deleteFile($itemId) {
        // Find files matching the pattern (for any extension)
        $pattern = $this->dataDir . '/' . $itemId . '.*';
        $files = glob($pattern);

        if (empty($files)) {
            // Also check for files without extension
            if (file_exists($this->dataDir . '/' . $itemId)) {
                $files[] = $this->dataDir . '/' . $itemId;
            }
        }

        if (!empty($files)) {
            foreach ($files as $file) {
                $fileName = basename($file);
                $this->appendToLog('DE', $fileName, '');
                unlink($file);
            }
            return true;
        }

        return false;
    }

    public function listFiles() {
        if (!file_exists($this->namesLogFile)) {
            return [];
        }

        $lines = file($this->namesLogFile, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);

        $items = [];
        $activeItems = [];

        foreach ($lines as $line) {
            $parts = explode(',', $line, 3);
            if (count($parts) < 3) continue;

            list($action, $itemId, $itemTitle) = $parts;

            // Extract extension if present
            $extension = '';
            if (strpos($itemId, '.') !== false) {
                list($id, $extension) = explode('.', $itemId, 2);
            } else {
                $id = $itemId;
            }

            switch ($action) {
                case 'CR':
                    $activeItems[$itemId] = [
                        'id' => $id,
                        'title' => $itemTitle,
                        'extension' => $extension
                    ];
                    break;
                case 'RN':
                    if (isset($activeItems[$itemId])) {
                        $activeItems[$itemId]['title'] = $itemTitle;
                    }
                    break;
                case 'DE':
                    unset($activeItems[$itemId]);
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
        // Try to find the file with or without extension
        $filePath = $this->dataDir . '/' . $itemId;

        if (!file_exists($filePath)) {
            // Look for files with this ID but any extension
            $pattern = $this->dataDir . '/' . $itemId . '.*';
            $files = glob($pattern);

            if (empty($files)) {
                return null;
            }

            $filePath = $files[0]; // Use the first match
        }

        return file_get_contents($filePath);
    }

    // Helper methods
    private function appendToLog($action, $itemId, $itemTitle) {
        $logLine = $action . ',' . $itemId . ',' . $itemTitle . PHP_EOL;
        file_put_contents($this->namesLogFile, $logLine, FILE_APPEND);
    }

    private function getCurrentTitle($itemId) {
        if (!file_exists($this->namesLogFile)) {
            return null;
        }

        $lines = file($this->namesLogFile, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);
        $title = null;

        foreach ($lines as $line) {
            $parts = explode(',', $line, 3);
            if (count($parts) < 3) continue;

            list($action, $id, $itemTitle) = $parts;

            if ($id === $itemId) {
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