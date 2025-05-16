<?php

class FileExporter {
    private $filesStorage;
    private $flatStorage;

    /**
     * Constructor
     *
     * @param FilesStorage $filesStorage The files storage instance
     * @param FlatStorage $flatStorage The flat storage instance
     */
    public function __construct($filesStorage, $flatStorage) {
        $this->filesStorage = $filesStorage;
        $this->flatStorage = $flatStorage;
    }

    /**
     * Export all files to a ZIP archive
     *
     * @return string Path to the created ZIP file
     */
    public function exportToZip() {
        // Create temporary directory
        $tempDir = sys_get_temp_dir() . '/export_' . uniqid();
        mkdir($tempDir, 0777, true);
        mkdir($tempDir . '/img', 0777, true);

        // Get all files from flat storage
        $allFiles = $this->flatStorage->getAllFiles();

        // Process all markdown files
        $markdownFiles = [];
        $imageFiles = [];

        foreach ($allFiles as $file) {
            $uuid = $file['id'];
            $extension = $file['extension'];

            // Get the content
            $content = $this->filesStorage->getContent($uuid . '.' . $extension);

            if ($extension === 'md') {
                // Process markdown content to update links
                $content = $this->processMarkdownContent($content);
                $markdownFiles[$uuid] = [
                    'title' => $file['title'],
                    'content' => $content,
                    'extension' => $extension
                ];
            } else {
                // Treat as image or other binary file
                $imageFiles[$uuid] = [
                    'title' => $file['title'],
                    'content' => $content,
                    'extension' => $extension
                ];
            }
        }

        // Create the directory structure and save files
        foreach ($markdownFiles as $uuid => $fileData) {
            $filePath = $tempDir . '/' . $this->sanitizeFilename($fileData['title']) . '.md';
            file_put_contents($filePath, $fileData['content']);
        }

        foreach ($imageFiles as $uuid => $fileData) {
            $filePath = $tempDir . '/img/' . $uuid . '.' . $fileData['extension'];
            file_put_contents($filePath, $fileData['content']);
        }

        // Create ZIP archive
        $zipPath = sys_get_temp_dir() . '/export_' . uniqid() . '.zip';
        $this->createZipArchive($tempDir, $zipPath);

        // Clean up temporary directory
        $this->removeDirectory($tempDir);

        return $zipPath;
    }

    /**
     * Process markdown content to replace API links with local paths
     *
     * @param string $content The markdown content
     * @return string The processed content
     */
    private function processMarkdownContent($content) {
        // Replace API links with local image paths
        $pattern = '/\[([^\]]+)\]\([^)]*api\/files\.php\?action=get&id=([^.]+)\.([^)]+)\)/';
        $replacement = '[$1](img/$2.$3)';

        return preg_replace($pattern, $replacement, $content);
    }

    /**
     * Create a ZIP archive from directory
     *
     * @param string $sourceDir The source directory
     * @param string $outputPath The output ZIP file path
     * @return bool Success status
     */
    private function createZipArchive($sourceDir, $outputPath) {
        $zip = new ZipArchive();

        if ($zip->open($outputPath, ZipArchive::CREATE | ZipArchive::OVERWRITE) === true) {
            $files = new RecursiveIteratorIterator(
                new RecursiveDirectoryIterator($sourceDir),
                RecursiveIteratorIterator::LEAVES_ONLY
            );

            foreach ($files as $name => $file) {
                if (!$file->isDir()) {
                    $filePath = $file->getRealPath();
                    $relativePath = substr($filePath, strlen($sourceDir) + 1);

                    $zip->addFile($filePath, $relativePath);
                }
            }

            $zip->close();
            return true;
        }

        return false;
    }

    /**
     * Remove directory recursively
     *
     * @param string $dir Directory path to remove
     */
    private function removeDirectory($dir) {
        if (!file_exists($dir)) {
            return;
        }

        $files = array_diff(scandir($dir), ['.', '..']);

        foreach ($files as $file) {
            $path = $dir . '/' . $file;

            if (is_dir($path)) {
                $this->removeDirectory($path);
            } else {
                unlink($path);
            }
        }

        rmdir($dir);
    }

    /**
     * Sanitize filename to be safe for file system
     *
     * @param string $filename The filename to sanitize
     * @return string Sanitized filename
     */
    private function sanitizeFilename($filename) {
        // Replace spaces with underscores
        $filename = str_replace(' ', '_', $filename);

        // Remove any characters that aren't alphanumeric, underscore, dash, or dot
        $filename = preg_replace('/[^\w\-\.]/', '', $filename);

        return $filename;
    }

    /**
     * Get the ZIP file as a download response
     *
     * @param string $zipPath Path to the ZIP file
     * @param string $filename Filename to use for download
     */
    public function downloadZip($zipPath, $filename = 'export.zip') {
        if (file_exists($zipPath)) {
            header('Content-Description: File Transfer');
            header('Content-Type: application/zip');
            header('Content-Disposition: attachment; filename="' . $filename . '"');
            header('Expires: 0');
            header('Cache-Control: must-revalidate');
            header('Pragma: public');
            header('Content-Length: ' . filesize($zipPath));
            readfile($zipPath);

            // Delete the temporary file
            unlink($zipPath);
            exit;
        }
    }
}