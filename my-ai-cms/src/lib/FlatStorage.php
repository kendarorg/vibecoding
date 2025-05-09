<?php

class FlatStorage {
    private string $dataDir;
    private string $structureDir;
    private string $indexLogPath;
    private string $namesLogPath;
    private string $rootUuid = '00000000-0000-0000-0000-000000000000';
    private string $rootName = 'Root';

    /**
     * Constructor
     */
    public function __construct(string $baseDir = 'data') {
        $this->dataDir = $baseDir;
        $this->structureDir = $baseDir . '/structure';
        $this->indexLogPath = $this->structureDir . '/index.log';
        $this->namesLogPath = $this->structureDir . '/names.log';

        // Ensure directories exist
        if (!file_exists($this->dataDir)) {
            mkdir($this->dataDir, 0755, true);
        }
        if (!file_exists($this->structureDir)) {
            mkdir($this->structureDir, 0755, true);
        }

        // Initialize log files if they don't exist
        if (!file_exists($this->indexLogPath)) {
            file_put_contents($this->indexLogPath, '');
        }
        if (!file_exists($this->namesLogPath)) {
            file_put_contents($this->namesLogPath, '');
        }
    }

    /**
     * Create or update an item
     */
    public function upsertItem(string $itemUuid, string $parentUuid, string $itemTitle, string $itemContent): void {
        $itemPath = $this->dataDir . '/' . $itemUuid;

        // Create or update the file content
        file_put_contents($itemPath, $itemContent);

        if (!file_exists($itemPath . '.tmp')) {
            // New item - add creation entries
            file_put_contents($this->indexLogPath, "CR,$itemUuid,$parentUuid\n", FILE_APPEND);
            file_put_contents($this->namesLogPath, "CR,$itemUuid,$itemTitle\n", FILE_APPEND);
            // Create a tmp file to mark item as existing
            file_put_contents($itemPath . '.tmp', '');
        } else {
            // Existing item - check for parent change
            $lastParent = $this->getLastParent($itemUuid);
            if ($lastParent !== $parentUuid) {
                file_put_contents($this->indexLogPath, "MV,$itemUuid,$parentUuid\n", FILE_APPEND);
            }

            // Check for name change
            $lastName = $this->getLastName($itemUuid);
            if ($lastName !== $itemTitle) {
                file_put_contents($this->namesLogPath, "RN,$itemUuid,$itemTitle\n", FILE_APPEND);
            }
        }
    }

    /**
     * Delete an item and its children recursively
     */
    public function deleteItem(string $itemUuid, string $parentUuid): void {
        // Prevent deleting root
        if ($itemUuid === $this->rootUuid) {
            throw new InvalidArgumentException('Cannot delete root item');
        }

        $itemPath = $this->dataDir . '/' . $itemUuid;

        if (file_exists($itemPath) || file_exists($itemPath . '.tmp')) {
            // Delete all children recursively
            $children = $this->listChildren($itemUuid);
            foreach ($children as $child) {
                $this->deleteItem($child['id'], $itemUuid);
            }

            // Add deletion entry
            file_put_contents($this->indexLogPath, "DE,$itemUuid,$parentUuid\n", FILE_APPEND);

            // Remove the tmp marker file
            if (file_exists($itemPath . '.tmp')) {
                unlink($itemPath . '.tmp');
            }

            // Remove the actual file
            if (file_exists($itemPath)) {
                unlink($itemPath);
            }
        }
    }

    /**
     * List all children of a specific parent
     */
    public function listChildren(string $parentUuid): array {
        $result = [];
        $indexLines = file($this->indexLogPath, FILE_IGNORE_NEW_LINES);

        if ($parentUuid === $this->rootUuid && empty($indexLines)) {
            return $result;
        }

        $latestItemStatus = [];

        // Process index file to find the latest status of each item
        if ($indexLines) {
            foreach ($indexLines as $line) {
                $parts = explode(',', $line);
                if (count($parts) === 3) {
                    list($action, $itemUuid, $itemParent) = $parts;

                    if ($action === 'CR' || $action === 'MV') {
                        $latestItemStatus[$itemUuid] = [
                            'parent' => $itemParent,
                            'exists' => true
                        ];
                    } elseif ($action === 'DE') {
                        $latestItemStatus[$itemUuid]['exists'] = false;
                    }
                }
            }
        }

        // Find children with specified parent
        $childrenIds = [];
        foreach ($latestItemStatus as $itemUuid => $status) {
            if ($status['parent'] === $parentUuid && $status['exists']) {
                $childrenIds[] = $itemUuid;
            }
        }

        // Get names for each child
        foreach ($childrenIds as $childId) {
            $title = $this->getLastName($childId);
            $result[] = [
                'id' => $childId,
                'title' => $title
            ];
        }

        return $result;
    }

    /**
     * Get content of a specific item
     */
    public function getContent(string $itemUuid): string {
        if ($itemUuid === $this->rootUuid) {
            return '';
        }

        $itemPath = $this->dataDir . '/' . $itemUuid;

        if (file_exists($itemPath)) {
            return file_get_contents($itemPath);
        }

        return '';
    }

    /**
     * Get the last known parent of an item
     */
    private function getLastParent(string $itemUuid): ?string {
        $lines = file($this->indexLogPath, FILE_IGNORE_NEW_LINES);
        $lastParent = null;

        if ($lines) {
            for ($i = count($lines) - 1; $i >= 0; $i--) {
                $parts = explode(',', $lines[$i]);
                if (count($parts) === 3 && $parts[1] === $itemUuid) {
                    if ($parts[0] === 'CR' || $parts[0] === 'MV') {
                        $lastParent = $parts[2];
                        break;
                    }
                }
            }
        }

        return $lastParent;
    }

    /**
     * Get the last known name of an item
     */
    private function getLastName(string $itemUuid): string {
        if ($itemUuid === $this->rootUuid) {
            return $this->rootName;
        }

        $lines = file($this->namesLogPath, FILE_IGNORE_NEW_LINES);
        $lastName = '';

        if ($lines) {
            for ($i = count($lines) - 1; $i >= 0; $i--) {
                $parts = explode(',', $lines[$i]);
                if (count($parts) === 3 && $parts[1] === $itemUuid) {
                    if ($parts[0] === 'CR' || $parts[0] === 'RN') {
                        $lastName = $parts[2];
                        break;
                    }
                }
            }
        }

        return $lastName;
    }
}