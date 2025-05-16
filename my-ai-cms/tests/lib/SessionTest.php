<?php

use PHPUnit\Framework\TestCase;

require_once  __DIR__ . '/../../src/lib/Utils.php';
require_once  __DIR__ . '/../../src/lib/Session.php';

class FakeSession extends Session{
    public function setCookie(string $name, string $value = "", int $expires_or_options = 0, string $path = "", string $domain = "", bool $secure = false, bool $httponly = false): bool
    {
        return true;
    }
}
/**
 * Unit tests for Session class
 */
class SessionTest extends TestCase
{
    private $tempDir;
    private $sessionDir;

    protected function setUp(): void
    {
        // Create temporary directory for sessions
        $this->tempDir = sys_get_temp_dir() . '/session_test_' . uniqid();
        $this->sessionDir = $this->tempDir . '/sessions';
        mkdir($this->tempDir, 0755, true);

        // Mock the cookie data
        $_COOKIE = [];
    }

    protected function tearDown(): void
    {
        // Clean up temporary files
        $this->deleteDir($this->tempDir);

        // Reset cookies
        $_COOKIE = [];
    }

    /**
     * Helper method to delete directory recursively
     */
    private function deleteDir($dir)
    {
        if (!file_exists($dir)) {
            return;
        }

        $files = array_diff(scandir($dir), ['.', '..']);
        foreach ($files as $file) {
            $path = "$dir/$file";
            is_dir($path) ? $this->deleteDir($path) : unlink($path);
        }

        return rmdir($dir);
    }

    /**
     * Mock for setcookie to test cookie functionality
     */
    private function mockCookieFunction()
    {
        // PHPUnit doesn't allow mocking global functions directly
        // Use runkit or similar for production code
    }

    public function testConstructorCreatesSessionDirectory()
    {
        $session = new FakeSession($this->tempDir);
        $this->assertDirectoryExists($this->sessionDir);
    }

    public function testNewSessionCreation()
    {
        $session = new FakeSession($this->tempDir);

        // Check if session ID was generated
        $sessionId = $session->getSessionId();
        $this->assertNotEmpty($sessionId);

        // Check if session file was created
        $this->assertFileExists($this->sessionDir . '/' . $sessionId . '.json');
    }

    public function testExistingValidSession()
    {
        // First create a session
        $session1 = new FakeSession($this->tempDir);
        $sessionId = $session1->getSessionId();

        // Mock cookie for next instantiation
        $_COOKIE['se'] = $sessionId;

        // Create new FakeSession instance with same cookie
        $session2 = new FakeSession($this->tempDir);

        // Should use the same session ID
        $this->assertEquals($sessionId, $session2->getSessionId());
    }

    public function testSetAndGetSessionData()
    {
        $session = new FakeSession($this->tempDir);

        // Test setting and getting data
        $session->set('test_key', 'test_value');
        $this->assertEquals('test_value', $session->get('test_key'));

        // Test default value for non-existent key
        $this->assertEquals('default', $session->get('non_existent', 'default'));
    }

    public function testHasMethod()
    {
        $session = new FakeSession($this->tempDir);

        $this->assertFalse($session->has('test_key'));

        $session->set('test_key', 'test_value');
        $this->assertTrue($session->has('test_key'));
    }

    public function testRemoveMethod()
    {
        $session = new FakeSession($this->tempDir);

        $session->set('test_key', 'test_value');
        $this->assertTrue($session->has('test_key'));

        $result = $session->remove('test_key');
        $this->assertTrue($result);
        $this->assertFalse($session->has('test_key'));

        // Test removing non-existent key
        $result = $session->remove('non_existent');
        $this->assertFalse($result);
    }

    public function testSessionDataPersistence()
    {
        // Create initial session and set data
        $session1 = new FakeSession($this->tempDir);
        $sessionId = $session1->getSessionId();
        $session1->set('test_key', 'test_value');

        // Mock cookie for new FakeSession instance
        $_COOKIE['se'] = $sessionId;

        // Create new FakeSession instance
        $session2 = new FakeSession($this->tempDir);

        // Check if data persists
        $this->assertEquals('test_value', $session2->get('test_key'));
    }

    public function testDestroySession()
    {
        $session = new FakeSession($this->tempDir);
        $sessionId = $session->getSessionId();
        $sessionFile = $this->sessionDir . '/' . $sessionId . '.json';

        $session->set('test_key', 'test_value');
        $this->assertFileExists($sessionFile);

        $session->destroy();

        // Session file should be deleted
        $this->assertFileDoesNotExist($sessionFile);

        // Data should be cleared
        $this->assertFalse($session->has('test_key'));
    }

    public function testExpiredSessionRenewal()
    {
        // This test requires mocking time() function or manipulating file timestamps
        // which is beyond the scope of simple unit testing
        $this->markTestSkipped('Requires time manipulation capabilities');
    }
}