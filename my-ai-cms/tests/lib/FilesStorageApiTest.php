<?php

require_once __DIR__ . '/../../src/lib/FilesStorageApi.php';

class FakeFilesStorage extends FilesStorage {
    private array $files = [];
    private array $fileContents = [];

    public function __construct() {
        // Skip parent constructor
    }

    public function upsertFile($fileId, $title, $content=null): string {
        $this->files[$fileId] = $title;
        if ($content !== null) {
            $this->fileContents[$fileId] = $content;
        }
        return $fileId;
    }

    public function listFiles(): array {
        return array_keys($this->files);
    }

    public function listFilesByExtension($extensions): array {
        $result = [];
        foreach (array_keys($this->files) as $fileId) {
            $ext = pathinfo($fileId, PATHINFO_EXTENSION);
            if (in_array($ext, $extensions)) {
                $result[] = $fileId;
            }
        }
        return $result;
    }

    public function getContent($fileId): ?string {
        return $this->fileContents[$fileId] ?? null;
    }

    public function getFileTitle(string $fileId): ?string {
        return $this->files[$fileId] ?? null;
    }

    public function deleteFile($fileId): bool {
        if (!isset($this->files[$fileId])) {
            return false;
        }

        unset($this->files[$fileId]);
        unset($this->fileContents[$fileId]);
        return true;
    }

    public function hasFile(string $fileId): bool {
        return isset($this->files[$fileId]);
    }
}

class TestableFilesStorageApi extends FilesStorageApi {
    private ?string $mockRequestBody = null;

    public function addHeader($header) {

    }
    public function setMockRequestBody(array $data): void {
        $this->mockRequestBody = json_encode($data);
    }

    protected function getRequestBody(): string {
        if ($this->mockRequestBody !== null) {
            return $this->mockRequestBody;
        }
        return parent::getRequestBody();
    }
}

class FilesStorageApiTest extends PHPUnit\Framework\TestCase {
    private FakeFilesStorage $fakeStorage;
    private TestableFilesStorageApi $api;

    protected function setUp(): void {
        $this->fakeStorage = new FakeFilesStorage();
        $this->api = new TestableFilesStorageApi($this->fakeStorage);

        // Create some initial test files
        $this->fakeStorage->upsertFile('test-file-1.txt', 'Test File 1', 'Test Content 1');
        $this->fakeStorage->upsertFile('test-file-2.md', 'Test File 2', 'Test Content 2');
        $this->fakeStorage->upsertFile('test-file-3.json', 'Test File 3', '{"test": "data"}');

        // Reset global variables
        $_GET = [];
        $_SERVER = [];
    }

    protected function tearDown(): void {
        // Clean up global variables
        $_GET = [];
        $_SERVER = [];
    }

    public function testListAllFiles(): void {
        // Mock $_GET and $_SERVER
        $_GET = ['action' => 'list'];
        $_SERVER['REQUEST_METHOD'] = 'GET';

        $response = $this->api->processRequest();

        $this->assertTrue($response['success']);
        $this->assertCount(3, $response['files']);
        $this->assertContains('test-file-1.txt', $response['files']);
        $this->assertContains('test-file-2.md', $response['files']);
        $this->assertContains('test-file-3.json', $response['files']);
    }

    public function testListFilesByExtension(): void {
        // Mock $_GET and $_SERVER
        $_GET = [
            'action' => 'list',
            'extension' => 'txt,json'
        ];
        $_SERVER['REQUEST_METHOD'] = 'GET';

        $response = $this->api->processRequest();

        $this->assertTrue($response['success']);
        $this->assertCount(2, $response['files']);
        $this->assertContains('test-file-1.txt', $response['files']);
        $this->assertContains('test-file-3.json', $response['files']);
        $this->assertNotContains('test-file-2.md', $response['files']);
    }

    public function testGetFileContent(): void {
        // Mock $_GET and $_SERVER
        $_GET = [
            'action' => 'content',
            'id' => 'test-file-1.txt'
        ];
        $_SERVER['REQUEST_METHOD'] = 'GET';

        $response = $this->api->processRequest();

        $this->assertTrue($response['success']);
        $this->assertEquals(base64_encode('Test Content 1'), $response['content']);
    }

    public function testGetFileForDownload(): void {
        // Mock $_GET and $_SERVER
        $_GET = [
            'action' => 'get',
            'id' => 'test-file-1.txt'
        ];
        $_SERVER['REQUEST_METHOD'] = 'GET';

        ob_start();
        $response = $this->api->processRequest();
        $output = ob_get_clean();

        $this->assertNull($response);
        $this->assertEquals('Test Content 1', $output);
    }

    public function testHandlePostRequestWithUploadAction(): void {
        // Mock $_FILES and $_POST
        $_FILES['file'] = [
            'name' => 'test-upload.txt',
            'type' => 'text/plain',
            'tmp_name' => 'php://memory',
            'error' => UPLOAD_ERR_OK,
            'size' => 20
        ];
        $_SERVER['REQUEST_METHOD'] = 'POST';
        $_GET = [
            'action' => 'upload'
        ];

        $_POST['title'] = 'Test Upload Title';

        // Mock file_get_contents to return test content
        $testContent = 'Test upload content';

        // Create a mock for the API that overrides necessary methods
       /* $apiMock = $this->getMockBuilder(FilesStorageApi::class)
            ->setConstructorArgs([$this->fakeStorage])
            ->onlyMethods(['getRequestBody'])
            ->getMock();

        // Setup the mock to return our test content
        $apiMock->expects($this->once())
            ->method('getRequestBody')
            ->willReturn(json_encode([
                'title' => 'Test Upload Title',
                'content' => base64_encode($testContent)
            ]));

        // Process the request
        $response = $apiMock->handlePostRequest('upload');*/


        $response = $this->api->processRequest();

        // Assertions
        $this->assertTrue($response['success']);
        $this->assertStringContainsString('File uploaded successfully', $response['message']);
        $this->assertArrayHasKey('id', $response);
        $this->assertArrayHasKey('title', $response);
        $this->assertArrayHasKey('url', $response);
        $this->assertEquals('Test Upload Title', $response['title']);

        // Verify the file was created in storage
        $savedContent = $this->fakeStorage->getContent($response['id']);
        $this->assertNotNull($savedContent);
    }

    public function testCreateFile(): void {
        // Mock $_GET, $_SERVER and input data
        $_GET = ['action' => 'create'];
        $_SERVER['REQUEST_METHOD'] = 'POST';

        $content = base64_encode('New File Content');
        $inputData = [
            'title' => 'New Test File',
            'extension' => 'txt',
            'content' => $content
        ];

        // Setup input stream
        $this->api->setMockRequestBody($inputData);

        $response = $this->api->processRequest();

        $this->assertTrue($response['success']);
        $this->assertNotEmpty($response['id']);

        // Verify file was created
        $fileId = $response['id'];
        $this->assertTrue($this->fakeStorage->hasFile($fileId));
        $this->assertEquals('New Test File', $this->fakeStorage->getFileTitle($fileId));
        $this->assertEquals('New File Content', $this->fakeStorage->getContent($fileId));
    }

    public function testUpdateFile(): void {
        // Mock $_GET, $_SERVER and input data
        $_GET = ['action' => 'update'];
        $_SERVER['REQUEST_METHOD'] = 'PUT';

        $content = base64_encode('Updated Content');
        $inputData = [
            'id' => 'test-file-1.txt',
            'title' => 'Updated Title',
            'content' => $content
        ];

        // Setup input stream
        $this->api->setMockRequestBody($inputData);

        $response = $this->api->processRequest();

        $this->assertTrue($response['success']);

        // Verify file was updated
        $this->assertEquals('Updated Title', $this->fakeStorage->getFileTitle('test-file-1.txt'));
        $this->assertEquals('Updated Content', $this->fakeStorage->getContent('test-file-1.txt'));
    }

    public function testUpdateTitleOnly(): void {
        // Mock $_GET, $_SERVER and input data
        $_GET = ['action' => 'update'];
        $_SERVER['REQUEST_METHOD'] = 'PUT';

        $inputData = [
            'id' => 'test-file-1.txt',
            'title' => 'Title Only Update',
            'content' => null
        ];

        // Setup input stream
        $this->api->setMockRequestBody($inputData);

        $response = $this->api->processRequest();

        $this->assertTrue($response['success']);

        // Verify only title was updated
        $this->assertEquals('Title Only Update', $this->fakeStorage->getFileTitle('test-file-1.txt'));
        $this->assertEquals('Test Content 1', $this->fakeStorage->getContent('test-file-1.txt'));
    }

    public function testDeleteFile(): void {
        // Mock $_GET and $_SERVER
        $_GET = [
            'action' => 'delete',
            'id' => 'test-file-1.txt'
        ];
        $_SERVER['REQUEST_METHOD'] = 'DELETE';

        $response = $this->api->processRequest();

        $this->assertTrue($response['success']);

        // Verify file was deleted
        $this->assertFalse($this->fakeStorage->hasFile('test-file-1.txt'));
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
        $this->assertStringContainsString('Missing file ID', $response['message']);
    }

    public function testMethodOverride(): void {
        // Test method override from GET to DELETE
        $_GET = [
            'action' => 'delete',
            'id' => 'test-file-2.md'
        ];
        $_SERVER['REQUEST_METHOD'] = 'GET';

        $response = $this->api->processRequest();

        $this->assertTrue($response['success']);
        $this->assertFalse($this->fakeStorage->hasFile('test-file-2.md'));
    }

    public function testUpdateContentOnly(): void {
        // Mock $_GET, $_SERVER and input data
        $_GET = ['action' => 'update'];
        $_SERVER['REQUEST_METHOD'] = 'PUT';

        $content = base64_encode('Updated Content Only');
        $inputData = [
            'id' => 'test-file-1.txt',
            'title' => 'Test File 1', // No title update
            'content' => $content
        ];

        // Setup input stream
        $this->api->setMockRequestBody($inputData);

        $response = $this->api->processRequest();

        $this->assertTrue($response['success']);

        // Verify only content was updated
        $this->assertEquals('Test File 1', $this->fakeStorage->getFileTitle('test-file-1.txt'));
        $this->assertEquals('Updated Content Only', $this->fakeStorage->getContent('test-file-1.txt'));
    }

    public function testFileNotFound(): void {
        // Mock $_GET and $_SERVER
        $_GET = [
            'action' => 'content',
            'id' => 'non-existent-file.txt'
        ];
        $_SERVER['REQUEST_METHOD'] = 'GET';

        $response = $this->api->processRequest();

        $this->assertFalse($response['success']);
        $this->assertStringContainsString('File not found', $response['message']);
    }

    public function testCreateFileWithMissingParameters(): void {
        // Mock $_GET, $_SERVER and input data
        $_GET = ['action' => 'create'];
        $_SERVER['REQUEST_METHOD'] = 'POST';

        $inputData = [
            // Missing title
            'extension' => 'txt',
            'content' => base64_encode('Some content')
        ];

        // Setup input stream
        $this->api->setMockRequestBody($inputData);

        $response = $this->api->processRequest();

        $this->assertFalse($response['success']);
        $this->assertStringContainsString('Missing file title', $response['message']);
    }

    public function testUpdateNonExistentFile(): void {
        // Mock $_GET, $_SERVER and input data
        $_GET = ['action' => 'update'];
        $_SERVER['REQUEST_METHOD'] = 'PUT';

        $inputData = [
            'id' => 'non-existent-file.txt',
            'title' => 'Updated Title',
            'content' => base64_encode('Updated Content')
        ];

        // Setup input stream
        $this->api->setMockRequestBody($inputData);

        $response = $this->api->processRequest();

        $this->assertTrue($response['success']);
    }

    public function testDeleteNonExistentFile(): void {
        // Mock $_GET and $_SERVER
        $_GET = [
            'action' => 'delete',
            'id' => 'non-existent-file.txt'
        ];
        $_SERVER['REQUEST_METHOD'] = 'DELETE';

        $response = $this->api->processRequest();

        $this->assertFalse($response['success']);
        $this->assertStringContainsString('File not found', $response['message']);
    }

    public function testEmptyExtensionList(): void {
        // Mock $_GET and $_SERVER
        $_GET = [
            'action' => 'list',
            'extension' => ''
        ];
        $_SERVER['REQUEST_METHOD'] = 'GET';

        $response = $this->api->processRequest();

        $this->assertTrue($response['success']);
        // Should return all files since no extension filter is effectively applied
        $this->assertCount(3, $response['files']);
    }

    public function testNonMatchingExtensionList(): void {
        // Mock $_GET and $_SERVER
        $_GET = [
            'action' => 'list',
            'extension' => 'php,html'
        ];
        $_SERVER['REQUEST_METHOD'] = 'GET';

        $response = $this->api->processRequest();

        $this->assertTrue($response['success']);
        // No files should match these extensions
        $this->assertCount(0, $response['files']);
    }

    public function testInvalidRequestMethod(): void {
        // Mock $_GET and $_SERVER with unsupported method
        $_GET = ['action' => 'create'];
        $_SERVER['REQUEST_METHOD'] = 'OPTIONS';

        $response = $this->api->processRequest();

        $this->assertFalse($response['success']);
        $this->assertStringContainsString('Unsupported HTTP method', $response['message']);
    }


}