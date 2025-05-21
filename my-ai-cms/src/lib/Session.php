<?php

class Session {
    private $sessionId;
    private $sessionData;
    private $sessionFile;
    private $isLoaded = false;
    private $cookieName = 'se';
    private $sessionDir;

    public function __construct($rootDir) {
        // Make sure sessions directory exists
        $this->sessionDir = rtrim($rootDir, '/') . '/sessions';
        if (!file_exists($this->sessionDir)) {
            mkdir($this->sessionDir, 0755, true);
        }

        $this->initialize();
    }


    private function initialize() {
        // Check if session cookie exists
        if (isset($_COOKIE[$this->cookieName])) {
            $this->sessionId = $_COOKIE[$this->cookieName];
            $this->sessionFile = $this->sessionDir . '/' . $this->sessionId . '.json';

            // Check if session file exists and is valid
            if (file_exists($this->sessionFile)) {
                $data = $this->loadSessionFile();

                // Check if session is expired
                if (isset($data['expire']) && strtotime($data['expire']) > time()) {
                    $this->sessionData = $data;
                    $this->isLoaded = true;

                    // Update expiration time
                    $this->setExpiration();
                    return;
                }

                // Session expired, delete file
                unlink($this->sessionFile);
            }

            // Invalid or expired session, clear cookie
            $this->setCookie($this->cookieName, '', time() - 3600, '/');
        }

        // Create new session
        $this->createNewSession();
    }

    private function createNewSession() {
        // Generate UUID v4
        $this->sessionId = Utils::generateUuid();
        $this->sessionFile = $this->sessionDir . '/' . $this->sessionId . '.json';

        // Set initial session data with expiration
        $this->sessionData = [
            'expire' => date('Y-m-d H:i:s', time() + 3600)
        ];

        // Save session file
        $this->saveSessionFile();

        // Set cookie
        $this->setCookie($this->cookieName, $this->sessionId, time() + 3600, '/');
    }

    private function loadSessionFile() {
        if (!$this->isLoaded && file_exists($this->sessionFile)) {
            $content = file_get_contents($this->sessionFile);
            return json_decode($content, true);
        }
        return $this->sessionData;
    }

    private function saveSessionFile() {
        file_put_contents($this->sessionFile, json_encode($this->sessionData));
    }

    private function setExpiration() {
        $this->sessionData['expire'] = date('Y-m-d H:i:s', time() + 3600);
        $this->saveSessionFile();
    }

    public function get($key, $default = null) {
        // Make sure session data is loaded
        if (!$this->isLoaded) {
            $this->sessionData = $this->loadSessionFile();
            $this->isLoaded = true;
        }

        return isset($this->sessionData[$key]) ? $this->sessionData[$key] : $default;
    }

    public function set($key, $value) {
        // Make sure session data is loaded
        if (!$this->isLoaded) {
            $this->sessionData = $this->loadSessionFile();
            $this->isLoaded = true;
        }

        $this->sessionData[$key] = $value;
        $this->saveSessionFile();
    }

    public function has($key) {
        // Make sure session data is loaded
        if (!$this->isLoaded) {
            $this->sessionData = $this->loadSessionFile();
            $this->isLoaded = true;
        }

        return isset($this->sessionData[$key]);
    }

    public function remove($key) {
        // Make sure session data is loaded
        if (!$this->isLoaded) {
            $this->sessionData = $this->loadSessionFile();
            $this->isLoaded = true;
        }

        if (isset($this->sessionData[$key])) {
            unset($this->sessionData[$key]);
            $this->saveSessionFile();
            return true;
        }

        return false;
    }

    public function destroy() {
        if (file_exists($this->sessionFile)) {
            unlink($this->sessionFile);
        }

        $this->setCookie($this->cookieName, '', 0, '/');
        unset($_COOKIE[$this->cookieName]);
        session_unset();
        session_destroy();
        $this->sessionData = [];
        $this->isLoaded = false;
    }

    public function getSessionId() {
        return $this->sessionId;
    }

    /**
     * @return void
     */
    public function setCookie(string $name, string $value = "", int $expires_or_options = 0, string $path = "", string $domain = "", bool $secure = false, bool $httponly = false): bool
    {
        return setcookie($name, $value, $expires_or_options, $path);
    }

    public function checkLoggedIn(){
        if (!$this->has('user')) {
            http_response_code(401);
            echo json_encode(['success' => false, 'error' => 'Authentication required']);
            exit;
        }
    }

    public function thisLoggedIn(){
        return $this->has('user');
    }
}