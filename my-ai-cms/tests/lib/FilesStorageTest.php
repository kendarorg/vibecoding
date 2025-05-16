<?php

require_once  __DIR__ . '/../../src/lib/FilesStorage.php';

class FilesStorageTest extends PHPUnit\Framework\TestCase {
    private $tempDataDir;
    private $tempStructureDir;
    private $storage;

    protected function setUp(): void {
        $target = '../../target/lib/files_storage_test';
        // Create a unique test directory
        $uniqueId = uniqid();
        $this->tempDataDir = $target.'/' . $uniqueId.'_data';
        $this->tempStructureDir = $target.'/' . $uniqueId.'_structure';

        // Create the directories
        if (!file_exists($target)) {
            mkdir($target, 0755, true);
        }

        $this->storage = new FilesStorage($this->tempDataDir,$this->tempStructureDir);
    }

    protected function tearDown(): void {
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

    public function testUpsertNewFile() {
        // Create a new file
        $this->storage->upsertFile('test123.txt', 'Test Document', 'This is a test content');

        // Check if the file exists
        $this->assertFileExists($this->tempDataDir.'/test123.txt');

        // Check if the content is correct
        $content = file_get_contents($this->tempDataDir.'/test123.txt');
        $this->assertEquals('This is a test content', $content);

        // Check if the log entry is correct - now using basename only
        $logContent = file_get_contents($this->tempStructureDir.'/names.log');
        $this->assertStringContainsString('CR,test123,Test Document', $logContent);
    }



    public function testUpsertNewEmptyFile() {
        // Create a new file
        $this->storage->upsertFile('test123.txt', 'Test Document', null);

        // Check if the file exists
        $this->assertFileExists($this->tempDataDir.'/test123.txt');

        // Check if the content is correct
        $content = file_get_contents($this->tempDataDir.'/test123.txt');
        $this->assertEquals('', $content);

        // Check if the log entry is correct - now using basename only
        $logContent = file_get_contents($this->tempStructureDir.'/names.log');
        $this->assertStringContainsString('CR,test123,Test Document', $logContent);
    }

    public function testUpsertExistingFileWithTitleChange() {
        // Create a file
        $this->storage->upsertFile('test456.md', 'Original Title', 'Original content');

        // Update the title
        $this->storage->upsertFile('test456.md', 'New Title', 'Updated content');

        // Check if the file was updated
        $content = file_get_contents($this->tempDataDir.'/test456.md');
        $this->assertEquals('Updated content', $content);

        // Check log entries - now using basename only
        $logContent = file_get_contents($this->tempStructureDir.'/names.log');
        $this->assertStringContainsString('CR,test456,Original Title', $logContent);
        $this->assertStringContainsString('RN,test456,New Title', $logContent);
    }

    public function testDeleteFile() {
        // Create a file
        $this->storage->upsertFile('delete_me.txt', 'Delete Test', 'Content to delete');

        // Check if the file exists
        $this->assertFileExists($this->tempDataDir.'/delete_me.txt');

        // Delete the file
        $result = $this->storage->deleteFile('delete_me');

        // Check if deletion was successful
        $this->assertTrue($result);

        // Check if the file is gone
        $this->assertFileDoesNotExist($this->tempDataDir.'/delete_me.txt');

        // Check log entries - now using basename only
        $logContent = file_get_contents($this->tempStructureDir.'/names.log');
        $this->assertStringContainsString('CR,delete_me,Delete Test', $logContent);
        $this->assertStringContainsString('DE,delete_me,', $logContent);
    }

    public function testListFiles() {
        // Create several files
        $this->storage->upsertFile('file1.txt', 'File One', 'Content 1');
        $this->storage->upsertFile('file2.md', 'File Two', 'Content 2');
        $this->storage->upsertFile('file3.json', 'File Three', 'Content 3');

        // Delete one file
        $this->storage->deleteFile('file2');

        // Update one file title
        $this->storage->upsertFile('file1.txt', 'File One Updated', null);

        // List all files
        $files = $this->storage->listFiles();

        // Check results
        $this->assertCount(2, $files);

        // Check that file1 is present with updated title
        $file1 = array_filter($files, function($file) {
            return $file['id'] === 'file1';
        });
        $this->assertCount(1, $file1);
        $file1 = reset($file1);
        $this->assertEquals('File One Updated', $file1['title']);
        $this->assertEquals('txt', $file1['extension']);

        // Check that file3 is present
        $file3 = array_filter($files, function($file) {
            return $file['id'] === 'file3';
        });
        $this->assertCount(1, $file3);
        $file3 = reset($file3);
        $this->assertEquals('File Three', $file3['title']);
        $this->assertEquals('json', $file3['extension']);

        // Ensure file2 is not present (it was deleted)
        $file2 = array_filter($files, function($file) {
            return $file['id'] === 'file2';
        });
        $this->assertCount(0, $file2);
    }

    public function testListFilesByExtension() {
        // Create several files with different extensions
        $this->storage->upsertFile('doc1.txt', 'Text Doc', 'Text content');
        $this->storage->upsertFile('doc2.md', 'Markdown Doc', 'Markdown content');
        $this->storage->upsertFile('doc3.txt', 'Another Text', 'More text');
        $this->storage->upsertFile('doc4.json', 'JSON Doc', '{"test": true}');

        // List only txt files
        $textFiles = $this->storage->listFilesByExtension('txt');

        // Check that only txt files are returned
        $this->assertCount(2, $textFiles);
        foreach ($textFiles as $file) {
            $this->assertEquals('txt', $file['extension']);
        }

        // List multiple extensions
        $mixedFiles = $this->storage->listFilesByExtension('md,json');

        // Check that only md and json files are returned
        $this->assertCount(2, $mixedFiles);
        foreach ($mixedFiles as $file) {
            $this->assertTrue(in_array($file['extension'], ['md', 'json']));
        }
    }



    public function testListFilesByExtensionEmpty() {
        // Create several files with different extensions
        $this->storage->upsertFile('doc1.txt', 'Text Doc', 'Text content');
        $this->storage->upsertFile('doc2.md', 'Markdown Doc', 'Markdown content');
        $this->storage->upsertFile('doc3.txt', 'Another Text', 'More text');
        $this->storage->upsertFile('doc4.json', 'JSON Doc', '{"test": true}');

        // List only txt files
        $textFiles = $this->storage->listFilesByExtension(null);

        // Check that only txt files are returned
        $this->assertCount(4, $textFiles);
    }

    public function testGetContent() {
        // Create a file
        $this->storage->upsertFile('content_test.txt', 'Content Test', 'This is the content to retrieve');

        // Get the content
        $content = $this->storage->getContent('content_test.txt');

        // Check if content is correct
        $this->assertEquals('This is the content to retrieve', $content);

        // Test getting content without extension in request
        $contentAlt = $this->storage->getContent('content_test');
        $this->assertEquals('This is the content to retrieve', $contentAlt);

        // Test getting content for non-existent file
        $nonExistentContent = $this->storage->getContent('does_not_exist');
        $this->assertNull($nonExistentContent);
    }

    public function testFileWithSpecialCharactersInTitle(): void
    {
        // Create file with special characters in title
        $specialTitle = 'Test,File\\with"special$characters*';
        $this->storage->upsertFile('special_title.txt', $specialTitle, 'Test Content');

        // List all files
        $files = $this->storage->listFiles();

        // Find our file
        $found = false;
        foreach ($files as $file) {
            if ($file['id'] === 'special_title') {
                $this->assertEquals($specialTitle, $file['title']);
                $found = true;
                break;
            }
        }
        $this->assertTrue($found, 'File with special characters in title should be found');
    }

    public function testEmptyDirectories(): void
    {
        // Create a new storage with empty directories
        $emptyDir = $this->tempDataDir . '_empty';
        $emptyStructure = $this->tempStructureDir . '_empty';

        if (!file_exists($emptyDir)) {
            mkdir($emptyDir, 0755, true);
        }

        if (!file_exists($emptyStructure)) {
            mkdir($emptyStructure, 0755, true);
        }

        $emptyStorage = new FilesStorage($emptyDir, $emptyStructure);

        // Test listing files in an empty storage
        $files = $emptyStorage->listFiles();
        $this->assertIsArray($files);
        $this->assertEmpty($files);

        // Clean up
        rmdir($emptyDir);
    }

    public function testDeleteNonExistentFile(): void
    {
        // This should return false but not throw an exception
        $result = $this->storage->deleteFile('non_existent_file');
        $this->assertFalse($result);

        // Verify the operation was still logged
        $namesLog = file_get_contents($this->tempStructureDir . '/names.log');
        $this->assertEquals("", $namesLog);
    }

    public function testUpsertFileWithNullContent(): void
    {
        // Create initial file
        $this->storage->upsertFile('null_content.txt', 'Initial Title', 'Initial content');

        // Update only the title, keeping content
        $this->storage->upsertFile('null_content.txt', 'Updated Title', null);

        // Verify content wasn't changed
        $content = file_get_contents($this->tempDataDir . '/null_content.txt');
        $this->assertEquals('Initial content', $content);

        // Verify title was updated
        $files = $this->storage->listFiles();
        $found = false;
        foreach ($files as $file) {
            if ($file['id'] === 'null_content') {
                $this->assertEquals('Updated Title', $file['title']);
                $found = true;
                break;
            }
        }
        $this->assertTrue($found, 'File with updated title should be found');
    }
}