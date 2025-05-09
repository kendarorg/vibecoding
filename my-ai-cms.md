### REQ 001

<step>
Create a php class called FlatStorage that
* Uses two files as "indexes"
	* "structure/index.log": contains a list of items in format ACTION,ITEM_UUID,PARENT_UUID
	* "structure/names.log": contains a list of items in format ACTION,ITEM_UUID,ITEM_TITLE
* The data will be stored under the directory "data"
* The root item uuid is '00000000-0000-0000-0000-000000000000' with name 'Root'
* The root item is fixed, not modifiable, not deletable and not stored on indexes.
</step>
<step>
Expose these methods
* upsertItem(ITEM_UUID,PARENT_UUID,ITEM_TITLE,ITEM_CONTENT)
	* If the file "data/ITEM_UUID" does not exists
		* Create the file "data/ITEM_UUID" with content ITEM_CONTENT
		* Add on "structure/index.log" a line CR,ITEM_UUID,PARENT_UUID
		* Add on "structure/names.log" a line CR,ITEM_UUID,ITEM_TITLE
	* Else if the file "data/ITEM_UUID" exists
		* If the last line with matching ITEM_UUID in "structure/index.log" has a different PARENT_UUID
			* Add on "structure/index.log" a line MV,ITEM_UUID,PARENT_UUID
		* If the last line with matching ITEM_UUID in "structure/names.log" has a different ITEM_TITLE
			* Add on "structure/names.log" a line RN,ITEM_UUID,ITEM_TITLE
* deleteItem(ITEM_UUID,PARENT_UUID)
	* If the file "data/ITEM_UUID" exists
		* For each line in "structure/index.log" with ITEM_UUID as PARENT_UUID
			* call deleteItem recursively
		* Add on "structure/index.log" a line DE,ITEM_UUID,PARENT_UUID
* listChildren(ITEM_UUID)
	* Find on "structure/index.log" all the lines with ITEM_UUID as PARENT_UUID
		* For each founded line take only the most recent
		* For each founded line retrieve the ITEM_TITLE
		* Return a list of object in format {"title":"ITEM_TITLE","id":"ITEM_ID"}
* getContent(ITEM_UUID)
	* Return the content of the file "data/ITEM_UUID"
</step>

### RES 001

I'll create the FlatStorage class according to your requirements. This class will manage file and directory operations using log files as indexes for the structure.

```php
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
```

This implementation:

1. Uses two log files to track structure and names of items
2. Handles the root item as a special case
3. Implements the four main methods: upsertItem, deleteItem, listChildren, and getContent
4. Uses temporary files to track item existence efficiently
5. Includes helper methods to get the last known parent and name of items
6. Properly handles cases like recursive deletion of items

The class follows a simple append-only log approach where operations are recorded sequentially, and the current state is determined by replaying these operations.

### FIX 001

Set all directories from constructor


### REQ 002

Create unit tests for the FlatSTorage class storing data temporarely on "test_storage" root directory and cleaning up afterwards


### RES 002

I'll create unit tests for the FlatStorage class using PHPUnit. The tests will use a temporary directory under "test_storage" and clean up after test execution.

```php
<?php

use PHPUnit\Framework\TestCase;

require_once('src/plib/FlatStorage.php');

class FlatStorageTest extends TestCase
{
    private FlatStorage $storage;
    private string $tempDir;
    private string $tempStructureDir;

    protected function setUp(): void
    {
        // Create a unique test directory
        $this->tempDir = 'test_storage/flatstorage_test_' . uniqid();
        $this->tempStructureDir = 'test_storage/structure_' . uniqid();
        
        // Create the directories
        if (!file_exists('test_storage')) {
            mkdir('test_storage', 0755, true);
        }
        
        // Initialize storage with test directories
        $this->storage = new FlatStorage($this->tempDir, $this->tempStructureDir);
    }

    protected function tearDown(): void
    {
        // Clean up test directories
        $this->recursiveRemoveDirectory($this->tempDir);
        $this->recursiveRemoveDirectory($this->tempStructureDir);
    }

    /**
     * Helper method to remove directory recursively
     */
    private function recursiveRemoveDirectory(string $directory): void
    {
        if (!is_dir($directory)) {
            return;
        }

        $items = scandir($directory);
        foreach ($items as $item) {
            if ($item === '.' || $item === '..') {
                continue;
            }

            $path = $directory . '/' . $item;
            if (is_dir($path)) {
                $this->recursiveRemoveDirectory($path);
            } else {
                unlink($path);
            }
        }

        rmdir($directory);
    }

    public function testUpsertNewItem(): void
    {
        $uuid = '11111111-1111-1111-1111-111111111111';
        $parentUuid = '00000000-0000-0000-0000-000000000000'; // Root
        $title = 'Test Item';
        $content = 'Test Content';

        $this->storage->upsertItem($uuid, $parentUuid, $title, $content);

        // Check data file was created
        $this->assertFileExists($this->tempDir . '/' . $uuid);
        $this->assertEquals($content, file_get_contents($this->tempDir . '/' . $uuid));
        
        // Check temp marker file was created
        $this->assertFileExists($this->tempDir . '/' . $uuid . '.tmp');
        
        // Check log entries
        $indexLog = file_get_contents($this->tempStructureDir . '/index.log');
        $namesLog = file_get_contents($this->tempStructureDir . '/names.log');
        
        $this->assertStringContainsString("CR,$uuid,$parentUuid", $indexLog);
        $this->assertStringContainsString("CR,$uuid,$title", $namesLog);
    }

    public function testUpsertExistingItemWithChanges(): void
    {
        // Create initial item
        $uuid = '22222222-2222-2222-2222-222222222222';
        $parentUuid = '00000000-0000-0000-0000-000000000000'; // Root
        $title = 'Initial Title';
        $content = 'Initial Content';

        $this->storage->upsertItem($uuid, $parentUuid, $title, $content);
        
        // Update with changes
        $newParentUuid = '33333333-3333-3333-3333-333333333333';
        $newTitle = 'Updated Title';
        $newContent = 'Updated Content';
        
        $this->storage->upsertItem($uuid, $newParentUuid, $newTitle, $newContent);
        
        // Check content was updated
        $this->assertEquals($newContent, file_get_contents($this->tempDir . '/' . $uuid));
        
        // Check log entries for updates
        $indexLog = file_get_contents($this->tempStructureDir . '/index.log');
        $namesLog = file_get_contents($this->tempStructureDir . '/names.log');
        
        $this->assertStringContainsString("MV,$uuid,$newParentUuid", $indexLog);
        $this->assertStringContainsString("RN,$uuid,$newTitle", $namesLog);
    }

    public function testUpsertExistingItemWithoutChanges(): void
    {
        // Create initial item
        $uuid = '44444444-4444-4444-4444-444444444444';
        $parentUuid = '00000000-0000-0000-0000-000000000000'; // Root
        $title = 'Stable Title';
        $content = 'Initial Content';

        $this->storage->upsertItem($uuid, $parentUuid, $title, $content);
        
        // Get initial log files length
        $indexLogSizeBefore = filesize($this->tempStructureDir . '/index.log');
        $namesLogSizeBefore = filesize($this->tempStructureDir . '/names.log');
        
        // Update without parent or title changes
        $newContent = 'Updated Content Without Other Changes';
        $this->storage->upsertItem($uuid, $parentUuid, $title, $newContent);
        
        // Check content was updated
        $this->assertEquals($newContent, file_get_contents($this->tempDir . '/' . $uuid));
        
        // Check that log files didn't grow (no new entries)
        $indexLogSizeAfter = filesize($this->tempStructureDir . '/index.log');
        $namesLogSizeAfter = filesize($this->tempStructureDir . '/names.log');
        
        $this->assertEquals($indexLogSizeBefore, $indexLogSizeAfter);
        $this->assertEquals($namesLogSizeBefore, $namesLogSizeAfter);
    }

    public function testListChildren(): void
    {
        $rootUuid = '00000000-0000-0000-0000-000000000000';
        
        // Create multiple items under root
        $uuid1 = '55555555-5555-5555-5555-555555555555';
        $uuid2 = '66666666-6666-6666-6666-666666666666';
        
        $this->storage->upsertItem($uuid1, $rootUuid, 'First Child', 'Content 1');
        $this->storage->upsertItem($uuid2, $rootUuid, 'Second Child', 'Content 2');
        
        // Create a nested item
        $uuid3 = '77777777-7777-7777-7777-777777777777';
        $this->storage->upsertItem($uuid3, $uuid1, 'Nested Child', 'Nested Content');
        
        // List root children
        $rootChildren = $this->storage->listChildren($rootUuid);
        
        $this->assertCount(2, $rootChildren);
        $this->assertEquals($uuid1, $rootChildren[0]['id']);
        $this->assertEquals('First Child', $rootChildren[0]['title']);
        $this->assertEquals($uuid2, $rootChildren[1]['id']);
        $this->assertEquals('Second Child', $rootChildren[1]['title']);
        
        // List nested children
        $nestedChildren = $this->storage->listChildren($uuid1);
        
        $this->assertCount(1, $nestedChildren);
        $this->assertEquals($uuid3, $nestedChildren[0]['id']);
        $this->assertEquals('Nested Child', $nestedChildren[0]['title']);
    }

    public function testDeleteItem(): void
    {
        $rootUuid = '00000000-0000-0000-0000-000000000000';
        
        // Create a parent item
        $parentUuid = '88888888-8888-8888-8888-888888888888';
        $this->storage->upsertItem($parentUuid, $rootUuid, 'Parent Item', 'Parent Content');
        
        // Create a child item
        $childUuid = '99999999-9999-9999-9999-999999999999';
        $this->storage->upsertItem($childUuid, $parentUuid, 'Child Item', 'Child Content');
        
        // Delete the parent (should delete child too)
        $this->storage->deleteItem($parentUuid, $rootUuid);
        
        // Check files are removed
        $this->assertFileDoesNotExist($this->tempDir . '/' . $parentUuid);
        $this->assertFileDoesNotExist($this->tempDir . '/' . $parentUuid . '.tmp');
        $this->assertFileDoesNotExist($this->tempDir . '/' . $childUuid);
        $this->assertFileDoesNotExist($this->tempDir . '/' . $childUuid . '.tmp');
        
        // Check log entries for deletion
        $indexLog = file_get_contents($this->tempStructureDir . '/index.log');
        
        $this->assertStringContainsString("DE,$childUuid,$parentUuid", $indexLog);
        $this->assertStringContainsString("DE,$parentUuid,$rootUuid", $indexLog);
    }

    public function testGetContent(): void
    {
        $uuid = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
        $content = 'Test Content For Retrieval';
        
        $this->storage->upsertItem($uuid, '00000000-0000-0000-0000-000000000000', 'Test Item', $content);
        
        $retrievedContent = $this->storage->getContent($uuid);
        $this->assertEquals($content, $retrievedContent);
    }

    public function testGetContentOfRootItem(): void
    {
        $rootContent = $this->storage->getContent('00000000-0000-0000-0000-000000000000');
        $this->assertEquals('', $rootContent);
    }

    public function testCannotDeleteRoot(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->storage->deleteItem('00000000-0000-0000-0000-000000000000', '');
    }

    public function testMoveItemBetweenParents(): void
    {
        $rootUuid = '00000000-0000-0000-0000-000000000000';
        
        // Create two parent items
        $parent1Uuid = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
        $parent2Uuid = 'cccccccc-cccc-cccc-cccc-cccccccccccc';
        
        $this->storage->upsertItem($parent1Uuid, $rootUuid, 'Parent 1', 'Parent 1 Content');
        $this->storage->upsertItem($parent2Uuid, $rootUuid, 'Parent 2', 'Parent 2 Content');
        
        // Create item under parent 1
        $itemUuid = 'dddddddd-dddd-dddd-dddd-dddddddddddd';
        $this->storage->upsertItem($itemUuid, $parent1Uuid, 'Item to Move', 'Movable Content');
        
        // Verify it's under parent 1
        $parent1Children = $this->storage->listChildren($parent1Uuid);
        $this->assertCount(1, $parent1Children);
        $this->assertEquals($itemUuid, $parent1Children[0]['id']);
        
        // Move item to parent 2
        $this->storage->upsertItem($itemUuid, $parent2Uuid, 'Item to Move', 'Movable Content');
        
        // Verify it's now under parent 2 and not under parent 1
        $parent1ChildrenAfter = $this->storage->listChildren($parent1Uuid);
        $parent2ChildrenAfter = $this->storage->listChildren($parent2Uuid);
        
        $this->assertCount(0, $parent1ChildrenAfter);
        $this->assertCount(1, $parent2ChildrenAfter);
        $this->assertEquals($itemUuid, $parent2ChildrenAfter[0]['id']);
    }
}
```

This test suite covers:

1. Basic CRUD operations (create, read, update, delete)
2. Hierarchical operations (listing children, moving items between parents)
3. Edge cases (root item handling, unchanged items)
4. Verifies that files are created and log entries are properly written
5. Sets up and tears down temporary storage for each test
6. Creates a unique test directory for each test run

Each test focuses on a specific functionality of the FlatStorage class, ensuring that the storage mechanisms work correctly and that log 
entries accurately reflect the operations performed.

### FIX 002

Include paths
location of the temporary files
Removed tests cache

### REQ 003

* The upsertItem should consider itemTitle and itemContent as optional
	* If the itemTitle is null, should not change the title
	* If the itemContent is null, should not change the content
* The .tmp files should not be used at all



### RES 003

I'll modify the FlatStorage class to make itemTitle and itemContent optional, and remove the .tmp files approach. Here's the updated implementation:

```php
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
     * @param string $parentUuid Parent identifier
     * @param string|null $itemTitle Optional title (null to keep existing)
     * @param string|null $itemContent Optional content (null to keep existing)
     */
    public function upsertItem(string $itemUuid, string $parentUuid, ?string $itemTitle = null, ?string $itemContent = null): void {
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
            // New item - add creation entries
            file_put_contents($this->indexLogPath, "CR,$itemUuid,$parentUuid\n", FILE_APPEND);
            
            // Only add name entry if title is provided
            if ($itemTitle !== null) {
                file_put_contents($this->namesLogPath, "CR,$itemUuid,$itemTitle\n", FILE_APPEND);
            }
        } else {
            // Existing item - check for parent change
            $lastParent = $this->getLastParent($itemUuid);
            if ($lastParent !== $parentUuid) {
                file_put_contents($this->indexLogPath, "MV,$itemUuid,$parentUuid\n", FILE_APPEND);
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
```

Key changes made:

1. Modified the `upsertItem` method:
   - Added optional parameters with nullable type hints for `itemTitle` and `itemContent`
   - Only update content if content is provided
   - Only update title if title is provided
   - Removed all .tmp file usage

2. Updated the `deleteItem` method:
   - Removed all references to .tmp files
   - Added logic to handle cases where item exists in logs but file is missing

3. Improved method documentation:
   - Added detailed parameter descriptions
   - Clarified optional parameters behavior

These changes ensure the FlatStorage class follows the new requirements while maintaining the same functionality for tracking item hierarchy and names.

### FIX 003

updated test


### REQ 004

Add the following tests on FlatStorageTest, just show me the new tests:

* Upsert with null title 
	* Verify the title was not changed
	* Verify content was changed
* Upsert with null content
	* Verify the content was not changed
	* Verify title was changed

### RES 004

Here are the new tests to verify the optional parameters in the `upsertItem` method:

```php
public function testUpsertWithNullTitle(): void
{
    // Create initial item
    $uuid = 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee';
    $parentUuid = '00000000-0000-0000-0000-000000000000';
    $initialTitle = 'Initial Title';
    $initialContent = 'Initial Content';

    $this->storage->upsertItem($uuid, $parentUuid, $initialTitle, $initialContent);

    // Update with null title, new content
    $newContent = 'Updated Content';
    $this->storage->upsertItem($uuid, $parentUuid, null, $newContent);

    // Verify content was changed
    $this->assertEquals($newContent, file_get_contents($this->tempDataDir . '/' . $uuid));

    // Verify title was not changed
    $children = $this->storage->listChildren($parentUuid);
    $matchingItem = null;
    foreach ($children as $item) {
        if ($item['id'] === $uuid) {
            $matchingItem = $item;
            break;
        }
    }

    $this->assertNotNull($matchingItem, 'Item should exist in parent\'s children');
    $this->assertEquals($initialTitle, $matchingItem['title'], 'Title should not have changed');

    // Check log files
    $namesLog = file_get_contents($this->tempStructureDir . '/names.log');
    $this->assertStringNotContainsString("RN,$uuid", $namesLog, 'Names log should not contain a rename entry');
}

public function testUpsertWithNullContent(): void
{
    // Create initial item
    $uuid = 'ffffffff-ffff-ffff-ffff-ffffffffffff';
    $parentUuid = '00000000-0000-0000-0000-000000000000';
    $initialTitle = 'Initial Title';
    $initialContent = 'Initial Content';

    $this->storage->upsertItem($uuid, $parentUuid, $initialTitle, $initialContent);

    // Update with new title, null content
    $newTitle = 'Updated Title';
    $this->storage->upsertItem($uuid, $parentUuid, $newTitle, null);

    // Verify content was not changed
    $this->assertEquals($initialContent, file_get_contents($this->tempDataDir . '/' . $uuid));

    // Verify title was changed
    $children = $this->storage->listChildren($parentUuid);
    $matchingItem = null;
    foreach ($children as $item) {
        if ($item['id'] === $uuid) {
            $matchingItem = $item;
            break;
        }
    }

    $this->assertNotNull($matchingItem, 'Item should exist in parent\'s children');
    $this->assertEquals($newTitle, $matchingItem['title'], 'Title should have changed');

    // Check log files
    $namesLog = file_get_contents($this->tempStructureDir . '/names.log');
    $this->assertStringContainsString("RN,$uuid,$newTitle", $namesLog, 'Names log should contain a rename entry');
}
```

These tests verify that:
1. When title is null, only the content gets updated
2. When content is null, only the title gets updated
3. Log entries are only created for fields that are actually changed