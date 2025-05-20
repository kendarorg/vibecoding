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
    public function __construct(string $dataDir = 'data',string $indexDir = 'structure') {
        $this->dataDir = $dataDir;
        $this->structureDir = $indexDir;
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
     * @param string $itemUuid Item identifier
     * @param string|null $parentUuid Parent identifier (null to keep existing parent)
     * @param string|null $itemTitle Optional title (null to keep existing)
     * @param string|null $itemContent Optional content (null to keep existing)
     */
    public function upsertItem(string $itemUuid, ?string $parentUuid = null, ?string $itemTitle = null, ?string $itemContent = null): void {
        $itemPath = $this->dataDir . '/' . $itemUuid;
        $itemExists = file_exists($itemPath);

        // Handle content update if provided
        if ($itemContent !== null) {

            file_put_contents($itemPath, $itemContent);
        } elseif (!$itemExists) {
            // Create empty file if it doesn't exist and no content was provided
            file_put_contents($itemPath, '');
        }

        if (!$itemExists) {
            // For new items, we must have a parent UUID
            if ($parentUuid === null) {
                if($itemUuid === $this->rootUuid){
                    return;
                }
                return;
            }

            // New item - add creation entries
            file_put_contents($this->indexLogPath, "CR,$itemUuid,$parentUuid\n", FILE_APPEND);

            // Only add name entry if title is provided
            if ($itemTitle !== null) {
                file_put_contents($this->namesLogPath, "CR,$itemUuid,$itemTitle\n", FILE_APPEND);
            }
        } else {
            // Existing item - check for parent change only if parentUuid is provided
            if ($parentUuid !== null) {
                $lastParent = $this->getLastParent($itemUuid);
                if ($lastParent !== $parentUuid) {
                    file_put_contents($this->indexLogPath, "MV,$itemUuid,$parentUuid\n", FILE_APPEND);
                }
            }

            // Check for name change, but only if a title was provided
            if ($itemTitle !== null) {
                $lastName = $this->getLastName($itemUuid);
                if ($lastName !== $itemTitle) {
                    file_put_contents($this->namesLogPath, "RN,$itemUuid,$itemTitle\n", FILE_APPEND);
                }
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

        if (file_exists($itemPath)) {
            // Delete all children recursively
            $children = $this->listChildren($itemUuid);
            foreach ($children as $child) {
                $this->deleteItem($child['id'], $itemUuid);
            }

            // Add deletion entry
            file_put_contents($this->indexLogPath, "DE,$itemUuid,$parentUuid\n", FILE_APPEND);

            // Remove the actual file
            unlink($itemPath);
        } else {
            // Check if item exists in logs but file is missing
            $lastParent = $this->getLastParent($itemUuid);
            if ($lastParent !== null) {
                // Still need to delete children and add deletion entry
                $children = $this->listChildren($itemUuid);
                foreach ($children as $child) {
                    $this->deleteItem($child['id'], $itemUuid);
                }

                file_put_contents($this->indexLogPath, "DE,$itemUuid,$parentUuid\n", FILE_APPEND);
            }
        }
    }

    public function getAllFiles()
    {
        $result = [];
        if ($handle = opendir($this->dataDir)) {

            while (false !== ($entry = readdir($handle))) {

                if ($entry != "." && $entry != "..") {
                    $result[]=[
                        'id' => $entry,
                        'title' => $this->getLastName($entry),
                        'parent' => $this->getLastParent($entry),
                    ];
                }
            }

            closedir($handle);
        }
        return $result;
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
        $itemPath = $this->dataDir . '/' . $itemUuid;

        if (file_exists($itemPath)) {
            return file_get_contents($itemPath);
        }

        return '';
    }



    /**
     * Get content of a specific item
     */
    public function exists(string $itemUuid): bool {
        $itemPath = $this->dataDir . '/' . $itemUuid;

        return file_exists($itemPath);
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
                $parts = explode(',', $lines[$i],3);
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

    /**
     * Check if an item has any children
     *
     * @param string $itemId The ID of the item to check
     * @return bool True if the item has children, false otherwise
     */
    public function hasChildren(string $itemId): bool {
        $children = $this->listChildren($itemId);
        return !empty($children);
    }

    /**
     * Get the full hierarchical path from root to the specified UUID
     *
     * @param string $uuid The unique identifier of the item
     * @return array The full path
     * @throws InvalidArgumentException If the UUID is invalid or not found
     */
    public function getFullPath(string $uuid): array {
        // Root UUID case - just return empty path with root
        if ($uuid === '00000000-0000-0000-0000-000000000000') {
            return [['id' => $uuid, 'title' => 'Root', 'parent' => null]];
        }

        // Get the index file path
        $indexFile = $this->structureDir . '/index.log';

        if (!file_exists($indexFile)) {
            throw new InvalidArgumentException('Index file not found');
        }

        // Build the parent-child relationship map from index.log
        $parentMap = [];
        $titleMap = [];

        $lines = file($indexFile, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);

        foreach ($lines as $line) {
            $parts = explode(',', $line, 4);
            if (count($parts) < 3) continue;
            $action=$parts[0];
            $id=$parts[1];
            $parent=$parts[2];
            if(!array_key_exists($id,$titleMap)){
                $titleMap[$id]=$this->getLastName($id);
            }

            switch ($action) {
                case 'CR':
                    $parentMap[$id] = $parent;
                    break;
                case 'MV':
                    $parentMap[$id] = $parent;
                    break;
                case 'DE':
                    unset($parentMap[$id]);
                    unset($titleMap[$id]);
                    break;
            }
        }

        // Item not found in the index
        if (!isset($parentMap[$uuid])) {
            throw new InvalidArgumentException('Item not found in index');
        }

        // Build the path by traversing up the parent hierarchy
        $path = [];
        $currentId = $uuid;

        while ($currentId && $currentId !== '00000000-0000-0000-0000-000000000000') {
            if (!isset($parentMap[$currentId])) {
                // Broken chain, can't find parent
                break;
            }

            // Add current item to the path
            array_unshift($path, [
                'id' => $currentId,
                'title' => $titleMap[$currentId] ?? 'Unknown',
                'parent' => $parentMap[$currentId]
            ]);

            // Move to parent
            $currentId = $parentMap[$currentId];
        }

        $result =[];
        // Add root to the beginning if not already there
        if (empty($path) || $path[0]['id'] !== '00000000-0000-0000-0000-000000000000') {
            $result[]=[
                'id' => '00000000-0000-0000-0000-000000000000',
                'title' => 'Root',
                'parent' => null
            ];
        }
        foreach ($path as $item){
            $result[]=$item;
        }

        return $result;
    }
}