<?php

use PHPUnit\Framework\TestCase;

require_once('../src/lib/FlatStorage.php');
require_once('../src/lib/FlatStorageApi.php');

class FlatStorageApiTest extends TestCase
{
    private FlatStorage $storage;
    private FlatStorageApi $api;
    private string $tempDataDir;
    private string $tempStructureDir;

    protected function setUp(): void
    {
        // Create a unique test directory
        $uniqueId = uniqid();
        $this->tempDataDir = '../target/' . $uniqueId.'_data';
        $this->tempStructureDir = '../target/' . $uniqueId.'_structure';

        // Create the directories
        if (!file_exists('../target')) {
            mkdir('../target', 0755, true);
        }

        // Initialize storage with test directories
        $this->storage = new FlatStorage($this->tempDataDir, $this->tempStructureDir);

        // Create API instance with mocked storage
        $this->api = new class($this->storage) extends FlatStorageApi {
            // Override getRequestBody to allow testing
            protected function getRequestBody(): string {
                return $this->mockRequestBody ?? '';
            }

            // Allow setting mock request body for testing
            public function setMockRequestBody(string $body): void {
                $this->mockRequestBody = $body;
            }

            // Expose handleGetRequest for testing
            public function testHandleGetRequest(?string $action): array {
                return $this->handleGetRequest($action);
            }
        };
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

    /**
     * Test listing items with handleGetRequest
     */
    public function testHandleGetRequestList(): void
    {
        // Setup test data - create some items
        $rootUuid = '00000000-0000-0000-0000-000000000000';
        $parentUuid = 'aaaa1111-aaaa-1111-aaaa-111111111111';
        $childUuid = 'aaaa2222-aaaa-2222-aaaa-222222222222';

        // Create parent under root
        $this->storage->upsertItem($parentUuid, $rootUuid, 'Parent Item', 'Parent Content');

        // Create child under parent
        $this->storage->upsertItem($childUuid, $parentUuid, 'Child Item', 'Child Content');

        // Mock $_GET parameters
        $_GET['action'] = 'list';
        $_GET['parent'] = $rootUuid;

        // Test listing root items
        $response = $this->api->testHandleGetRequest('list');

        // Assertions for root listing
        $this->assertTrue($response['success']);
        $this->assertEquals($rootUuid, $response['parent']);
        $this->assertCount(1, $response['items']);
        $this->assertEquals($parentUuid, $response['items'][0]['id']);
        $this->assertEquals('Parent Item', $response['items'][0]['title']);
        $this->assertTrue($response['items'][0]['hasChildren']);
        $this->assertEquals($rootUuid, $response['items'][0]['parent']);

        // Test listing parent's children
        $_GET['parent'] = $parentUuid;
        $response = $this->api->testHandleGetRequest('list');

        // Assertions for parent's children
        $this->assertTrue($response['success']);
        $this->assertEquals($parentUuid, $response['parent']);
        $this->assertCount(1, $response['items']);
        $this->assertEquals($childUuid, $response['items'][0]['id']);
        $this->assertEquals('Child Item', $response['items'][0]['title']);
        $this->assertFalse($response['items'][0]['hasChildren']);
        $this->assertEquals($parentUuid, $response['items'][0]['parent']);
    }

    /**
     * Test getting content with handleGetRequest
     */
    public function testHandleGetRequestContent(): void
    {
        // Setup test data
        $itemUuid = 'bbbb1111-bbbb-1111-bbbb-111111111111';
        $content = 'Test content for API test';

        // Create item
        $this->storage->upsertItem($itemUuid, '00000000-0000-0000-0000-000000000000', 'Test Item', $content);

        // Mock $_GET parameters
        $_GET['action'] = 'content';
        $_GET['id'] = $itemUuid;

        // Test getting content
        $response = $this->api->testHandleGetRequest('content');

        // Assertions
        $this->assertTrue($response['success']);
        $this->assertEquals($content, $response['content']);
    }

    /**
     * Test invalid action with handleGetRequest
     */
    public function testHandleGetRequestInvalidAction(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->api->testHandleGetRequest('invalid_action');
    }

    /**
     * Test missing item ID with content action
     */
    public function testHandleGetRequestContentMissingId(): void
    {
        $_GET['action'] = 'content';
        unset($_GET['id']);

        $this->expectException(InvalidArgumentException::class);
        $this->api->testHandleGetRequest('content');
    }
}