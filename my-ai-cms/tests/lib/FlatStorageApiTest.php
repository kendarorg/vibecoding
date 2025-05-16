<?php

use PHPUnit\Framework\TestCase;

require_once  __DIR__ . '/../../src/lib/FlatStorageApi.php';

/**
 * Test version of FlatStorageApi that allows mocking the request body
 */
class TestFlatStorageApi extends FlatStorageApi {
    private string $mockRequestBody = '';

    public function setMockRequestBody(array $data): void {
        $this->mockRequestBody = json_encode($data);
    }

    protected function getRequestBody(): string {
        return $this->mockRequestBody;
    }
}

/**
 * Mock implementation of FlatStorage for testing
 */
class FakeFlatStorage extends FlatStorage {
    private array $items = [];
    private array $contents = [];

    // Override the constructor to avoid actual file operations
    public function __construct() {
        // No initialization needed for fake implementation
    }

    public function upsertItem(string $itemUuid, ?string $parentUuid = null, ?string $itemTitle = null, ?string $itemContent = null): void {
        // Create item if it doesn't exist
        if (!isset($this->items[$itemUuid])) {
            $this->items[$itemUuid] = [
                'id' => $itemUuid,
                'parent' => $parentUuid,
                'title' => $itemTitle ?? 'Untitled'
            ];
            $this->contents[$itemUuid] = $itemContent ?? '';
        } else {
            // Update parent if provided
            if ($parentUuid) {
                $this->items[$itemUuid]['parent'] = $parentUuid;
            }

            // Update title if provided
            if ($itemTitle !== null) {
                $this->items[$itemUuid]['title'] = $itemTitle;
            }

            // Update content if provided
            if ($itemContent !== null) {
                $this->contents[$itemUuid] = $itemContent;
            }
        }
    }

    public function deleteItem(string $itemUuid, string $parentUuid): void {
        if ($itemUuid === '00000000-0000-0000-0000-000000000000') {
            throw new InvalidArgumentException('Cannot delete root item');
        }

        // Delete item and its children
        $children = $this->listChildren($itemUuid);
        foreach ($children as $child) {
            $this->deleteItem($child['id'], $itemUuid);
        }

        // Remove the item
        unset($this->items[$itemUuid]);
        unset($this->contents[$itemUuid]);
    }

    public function listChildren(string $parentUuid): array {
        $children = [];

        foreach ($this->items as $itemUuid => $item) {
            if ($item['parent'] === $parentUuid) {
                $children[] = [
                    'id' => $itemUuid,
                    'title' => $item['title']
                ];
            }
        }

        return $children;
    }

    public function getContent(string $itemUuid): string {
        if ($itemUuid === '00000000-0000-0000-0000-000000000000') {
            return '';
        }

        return $this->contents[$itemUuid] ?? '';
    }

    // Helper methods for test verification
    public function hasItem(string $itemUuid): bool {
        return isset($this->items[$itemUuid]);
    }

    public function getItemParent(string $itemUuid): ?string {
        return $this->items[$itemUuid]['parent'] ?? null;
    }

    public function getItemTitle(string $itemUuid): ?string {
        return $this->items[$itemUuid]['title'] ?? null;
    }
}

class FlatStorageApiTest extends TestCase {
    private FakeFlatStorage $fakeStorage;
    private TestFlatStorageApi $api;

    protected function setUp(): void {
        $this->fakeStorage = new FakeFlatStorage();
        $this->api = new TestFlatStorageApi($this->fakeStorage);

        // Setup test data
        $this->fakeStorage->upsertItem(
            '11111111-1111-1111-1111-111111111111',
            '00000000-0000-0000-0000-000000000000',
            'Test Item 1',
            'Test Content 1'
        );
    }

    public function testListItems(): void {
        // Mock $_GET and $_SERVER
        $_GET = ['action' => 'list'];
        $_SERVER['REQUEST_METHOD'] = 'GET';

        $response = $this->api->processRequest();

        $this->assertTrue($response['success']);
        $this->assertIsArray($response['items']);
        $this->assertCount(1, $response['items']);
        $this->assertEquals('11111111-1111-1111-1111-111111111111', $response['items'][0]['id']);
        $this->assertEquals('Test Item 1', $response['items'][0]['title']);
    }

    public function testGetContent(): void {
        // Mock $_GET and $_SERVER
        $_GET = [
            'action' => 'content',
            'id' => '11111111-1111-1111-1111-111111111111'
        ];
        $_SERVER['REQUEST_METHOD'] = 'GET';

        $response = $this->api->processRequest();

        $this->assertTrue($response['success']);
        $this->assertEquals('Test Content 1', $response['content']);
    }

    public function testCreateItem(): void {
        // Mock $_GET, $_SERVER and input data
        $_GET = ['action' => 'create'];
        $_SERVER['REQUEST_METHOD'] = 'POST';

        $inputData = [
            'id' => '22222222-2222-2222-2222-222222222222',
            'parent' => '11111111-1111-1111-1111-111111111111',
            'title' => 'New Item',
            'content' => 'New Content'
        ];

        // Setup input stream
        $this->api->setMockRequestBody($inputData);

        $response = $this->api->processRequest();

        $this->assertTrue($response['success']);
        $this->assertEquals('22222222-2222-2222-2222-222222222222', $response['id']);

        // Verify the item was created in storage
        $this->assertTrue($this->fakeStorage->hasItem('22222222-2222-2222-2222-222222222222'));
        $this->assertEquals('11111111-1111-1111-1111-111111111111', $this->fakeStorage->getItemParent('22222222-2222-2222-2222-222222222222'));
        $this->assertEquals('New Item', $this->fakeStorage->getItemTitle('22222222-2222-2222-2222-222222222222'));
        $this->assertEquals('New Content', $this->fakeStorage->getContent('22222222-2222-2222-2222-222222222222'));
    }

    public function testUpdateItemTitleOnly(): void {
        // Mock $_GET, $_SERVER and input data
        $_GET = ['action' => 'update'];
        $_SERVER['REQUEST_METHOD'] = 'PUT';

        $inputData = [
            'id' => '11111111-1111-1111-1111-111111111111',
            'parent' => '00000000-0000-0000-0000-000000000000',
            'title' => 'Updated Title'
        ];

        // Setup input stream
        $this->api->setMockRequestBody($inputData);

        $response = $this->api->processRequest();

        $this->assertTrue($response['success']);

        // Verify only the title was updated
        $this->assertEquals('Updated Title', $this->fakeStorage->getItemTitle('11111111-1111-1111-1111-111111111111'));
        $this->assertEquals('Test Content 1', $this->fakeStorage->getContent('11111111-1111-1111-1111-111111111111'));
    }

    public function testUpdateItemContentOnly(): void {
        // Mock $_GET, $_SERVER and input data
        $_GET = ['action' => 'update'];
        $_SERVER['REQUEST_METHOD'] = 'PUT';

        $inputData = [
            'id' => '11111111-1111-1111-1111-111111111111',
            'parent' => '00000000-0000-0000-0000-000000000000',
            'content' => 'Updated Content'
        ];

        // Setup input stream
        $this->api->setMockRequestBody($inputData);

        $response = $this->api->processRequest();

        $this->assertTrue($response['success']);

        // Verify only the content was updated
        $this->assertEquals('Test Item 1', $this->fakeStorage->getItemTitle('11111111-1111-1111-1111-111111111111'));
        $this->assertEquals('Updated Content', $this->fakeStorage->getContent('11111111-1111-1111-1111-111111111111'));
    }

    public function testMoveItem(): void {
        // Create another parent item
        $this->fakeStorage->upsertItem(
            '33333333-3333-3333-3333-333333333333',
            '00000000-0000-0000-0000-000000000000',
            'Another Parent',
            ''
        );

        // Mock $_GET, $_SERVER and input data
        $_GET = ['action' => 'update'];
        $_SERVER['REQUEST_METHOD'] = 'PUT';

        $inputData = [
            'id' => '11111111-1111-1111-1111-111111111111',
            'parent' => '33333333-3333-3333-3333-333333333333'
        ];

        // Setup input stream
        $this->api->setMockRequestBody($inputData);

        $response = $this->api->processRequest();

        $this->assertTrue($response['success']);

        // Verify the item was moved
        $this->assertEquals('33333333-3333-3333-3333-333333333333', $this->fakeStorage->getItemParent('11111111-1111-1111-1111-111111111111'));
    }

    public function testDeleteItem(): void {
        // Mock $_GET and $_SERVER
        $_GET = [
            'action' => 'delete',
            'id' => '11111111-1111-1111-1111-111111111111',
            'parent' => '00000000-0000-0000-0000-000000000000'
        ];
        $_SERVER['REQUEST_METHOD'] = 'DELETE';

        $response = $this->api->processRequest();

        $this->assertTrue($response['success']);

        // Verify the item was deleted
        $this->assertFalse($this->fakeStorage->hasItem('11111111-1111-1111-1111-111111111111'));
    }

    public function testInvalidAction(): void {
        // Mock $_GET and $_SERVER
        $_GET = ['action' => 'invalid'];
        $_SERVER['REQUEST_METHOD'] = 'GET';

        $response = $this->api->processRequest();

        $this->assertFalse($response['success']);
        $this->assertStringContainsString('Invalid action', $response['message']);
    }

    public function testMissingRequiredParameters(): void {
        // Test missing ID for content retrieval
        $_GET = ['action' => 'content'];
        $_SERVER['REQUEST_METHOD'] = 'GET';

        $response = $this->api->processRequest();

        $this->assertFalse($response['success']);
        $this->assertStringContainsString('Missing item ID', $response['message']);
    }

    /**
     * Helper method to simulate input data for POST/PUT requests
     */
    private function setupInputData(array $data): void {
        // Create a temp stream
        $tempStream = fopen('php://temp', 'r+');
        fwrite($tempStream, json_encode($data));
        rewind($tempStream);

        // Store the original input stream
        $origInputStream = $GLOBALS['HTTP_RAW_POST_DATA'] ?? '';

        // Mock the input stream
        $GLOBALS['HTTP_RAW_POST_DATA'] = $tempStream;

        // Use runkit to mock file_get_contents for php://input
        $this->mockFileGetContents(json_encode($data));
    }

    /**
     * Mock file_get_contents for php://input
     * Note: In a real environment, you might need runkit extension or similar
     */
    private function mockFileGetContents(string $returnData): void {
        // This is a simplified mock approach for tests
        // In a real environment with PHPUnit, you would use a more robust approach

        // For this example, we're assuming the file_get_contents is called in the API class
        // and we're mocking the return value for the test
    }

    public function testUpdateItemWithNullParent(): void {
        // Create an item to test with
        $initialParentId = '33333333-3333-3333-3333-333333333333';
        $this->fakeStorage->upsertItem(
            '44444444-4444-4444-4444-444444444444',
            $initialParentId,
            'Test Item for Null Parent',
            'Content'
        );

        // Verify initial parent
        $this->assertEquals($initialParentId, $this->fakeStorage->getItemParent('44444444-4444-4444-4444-444444444444'));

        // Mock $_GET, $_SERVER and input data
        $_GET = ['action' => 'update'];
        $_SERVER['REQUEST_METHOD'] = 'PUT';

        $inputData = [
            'id' => '44444444-4444-4444-4444-444444444444',
            'title' => 'Updated Title',
            // Explicitly excluding parent field to test null handling
        ];

        // Set mock request body
        $this->api->setMockRequestBody($inputData);

        $response = $this->api->processRequest();

        $this->assertTrue($response['success']);

        // Verify the title was updated but parent remained unchanged
        $this->assertEquals('Updated Title', $this->fakeStorage->getItemTitle('44444444-4444-4444-4444-444444444444'));
        $this->assertEquals($initialParentId, $this->fakeStorage->getItemParent('44444444-4444-4444-4444-444444444444'));
    }
}