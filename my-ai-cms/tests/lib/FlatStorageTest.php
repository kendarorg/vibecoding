<?php

use PHPUnit\Framework\TestCase;

require_once('../../src/lib/FlatStorage.php');

class FlatStorageTest extends TestCase
{
    private FlatStorage $storage;
    private string $tempDataDir;
    private string $tempStructureDir;

    protected function setUp(): void
    {
        $target = '../../target/lib/flat_storage_test';
        // Create a unique test directory
        $uniqueId = uniqid();
        $this->tempDataDir = $target.'/' . $uniqueId.'_data';
        $this->tempStructureDir = $target.'/' . $uniqueId.'_structure';

        // Create the directories
        if (!file_exists($target)) {
            mkdir($target, 0755, true);
        }

        // Initialize storage with test directories
        $this->storage = new FlatStorage($this->tempDataDir, $this->tempStructureDir);
    }

    protected function tearDown(): void
    {
        // Clean up test directories
        $this->recursiveRemoveDirectory($this->tempDataDir);
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
        $this->assertFileExists($this->tempDataDir . '/' . $uuid);
        $this->assertEquals($content, file_get_contents($this->tempDataDir . '/' . $uuid));

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
        $this->assertEquals($newContent, file_get_contents($this->tempDataDir . '/' . $uuid));

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
        $this->assertEquals($newContent, file_get_contents($this->tempDataDir . '/' . $uuid));

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
        $this->assertFileDoesNotExist($this->tempDataDir . '/' . $parentUuid);
        $this->assertFileDoesNotExist($this->tempDataDir . '/' . $parentUuid . '.tmp');
        $this->assertFileDoesNotExist($this->tempDataDir . '/' . $childUuid);
        $this->assertFileDoesNotExist($this->tempDataDir . '/' . $childUuid . '.tmp');

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

    public function testUpsertItemWithNullParent(): void
    {
        // Create initial item with a known parent
        $uuid = 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1';
        $initialParentUuid = '00000000-0000-0000-0000-000000000000'; // Root
        $initialTitle = 'Initial Title';
        $initialContent = 'Initial Content';

        $this->storage->upsertItem($uuid, $initialParentUuid, $initialTitle, $initialContent);

        // Now create another parent to move to first
        $newParentUuid = 'b2b2b2b2-b2b2-b2b2-b2b2-b2b2b2b2b2b2';
        $this->storage->upsertItem($newParentUuid, $initialParentUuid, 'New Parent', 'New Parent Content');

        // Move item to the new parent
        $this->storage->upsertItem($uuid, $newParentUuid, $initialTitle, $initialContent);

        // Verify it was moved
        $children = $this->storage->listChildren($newParentUuid);
        $this->assertCount(1, $children);
        $this->assertEquals($uuid, $children[0]['id']);

        // Get initial log file size to check for changes later
        $indexLogSizeBefore = filesize($this->tempStructureDir . '/index.log');

        // Now update the item with null parent (should not change parent)
        $newTitle = 'Updated Title';
        $newContent = 'Updated Content';
        $this->storage->upsertItem($uuid, null, $newTitle, $newContent);

        // Verify content and title were updated
        $this->assertEquals($newContent, file_get_contents($this->tempDataDir . '/' . $uuid));

        // Verify parent has not changed
        $children = $this->storage->listChildren($newParentUuid);
        $this->assertCount(1, $children);
        $this->assertEquals($uuid, $children[0]['id']);
        $this->assertEquals($newTitle, $children[0]['title']);

        // Verify no parent change was logged
        $indexLogSizeAfter = filesize($this->tempStructureDir . '/index.log');
        $this->assertEquals($indexLogSizeBefore, $indexLogSizeAfter, 'Index log should not have grown');
    }

    public function testHasChildren(): void
    {
        $rootUuid = '00000000-0000-0000-0000-000000000000';

        // Create a parent item
        $parentUuid = 'cccc1111-cccc-1111-cccc-111111111111';
        $this->storage->upsertItem($parentUuid, $rootUuid, 'Parent Item', 'Parent Content');

        // Initially, parent should have no children
        $this->assertFalse($this->storage->hasChildren($parentUuid));

        // Create a child item
        $childUuid = 'cccc2222-cccc-2222-cccc-222222222222';
        $this->storage->upsertItem($childUuid, $parentUuid, 'Child Item', 'Child Content');

        // Now parent should have children
        $this->assertTrue($this->storage->hasChildren($parentUuid));

        // Root should have children
        $this->assertTrue($this->storage->hasChildren($rootUuid));

        // Child should have no children
        $this->assertFalse($this->storage->hasChildren($childUuid));

        // Delete the child
        $this->storage->deleteItem($childUuid, $parentUuid);

        // Parent should no longer have children
        $this->assertFalse($this->storage->hasChildren($parentUuid));
    }
}