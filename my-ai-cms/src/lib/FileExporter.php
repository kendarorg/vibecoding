<?php

require_once "Utils.php";
require_once "Markdown.php";
require_once "export.php";


class FileExporter
{
    private $filesStorage;
    private $flatStorage;

    /**
     * Constructor
     *
     * @param FilesStorage $filesStorage The files storage instance
     * @param FlatStorage $flatStorage The flat storage instance
     */
    public function __construct($filesStorage, $flatStorage)
    {
        $this->filesStorage = $filesStorage;
        $this->flatStorage = $flatStorage;
    }

    /**
     * Export all files to a ZIP archive
     * @param string $type Type of content to export (e.g., 'all', 'md', 'image')
     * @return string Path to the created ZIP file
     */
    public function exportToZip($type)
    {
        // Create temporary directory
        $tempDir = sys_get_temp_dir() . '/export_' . uniqid();
        mkdir($tempDir, 0777, true);
        try {
            mkdir($tempDir . '/img', 0777, true);

            // Get all files from flat storage
            $allFiles = $this->flatStorage->getAllFiles();

            if ($type === 'all' || $type === 'browsable') {
                Utils::info("Generating index.html");
                // Generate index.html with a tree menu
                $this->generateIndexHtml($tempDir, $allFiles);
            }

            if ($type === 'images' || $type === 'all' || $type === 'browsable') {
                Utils::info("Generating images");
                // Process all markdown files
                $imageFiles = $this->filesStorage->listFiles();


                foreach ($imageFiles as $fileData) {
                    $uuid = $fileData['id'];
                    $filePath = $tempDir . '/img/' . $uuid . '.' . $fileData['extension'];
                    $content = $this->filesStorage->getContent($uuid);
                    if ($content) {
                        file_put_contents(Utils::sanitizeFileName($filePath), $content);
                    }
                }
            }


            $markdownFiles = [];
            foreach ($allFiles as $file) {
                $uuid = $file['id'];

                // Get the content
                $content = $this->flatStorage->getContent($uuid);

                // Process markdown content to update links
                $markdownFiles[$uuid] = [
                    'title' => $file['title'],
                    'content' => $content
                ];
            }

            $mkd = Markdown::new();


            if ($type === 'all' || $type === 'browsable') {
                Utils::info("Generating scripts");
                file_put_contents($tempDir . "/script.js", buildJavascript());
                file_put_contents($tempDir . "/style.css", buildStyle());
            }
            if ($type === 'all' || $type === 'browsable' || $type === 'md') {
                Utils::info("Generating ".$type);
                // Create the directory structure and save files
                foreach ($markdownFiles as $uuid => $fileData) {
                    $result = [];
                    $arrayPath = $this->flatStorage->getFullPath($uuid);
                    foreach ($arrayPath as $item) {
                        $result[] = $item["id"];
                    }

                    $filePath = join("/", $result);
                    $dirPath = $tempDir . '/' . dirname($filePath);
                    if (!file_exists($dirPath)) {
                        mkdir($dirPath, 077, true);
                    }

                    $mdPath = $tempDir . '/' . $filePath . '.md';
                    $htmlPath = $tempDir . '/' . $filePath . '.html';

                    $content = $this->processMarkdownContent($fileData['content'], count($arrayPath) - 1);
                    if ($type === 'all' || $type === 'md') {
                        Utils::info("Generating ".$item['title']." markdown");
                        file_put_contents(Utils::sanitizeFileName($mdPath),
                            $content);
                    }

                    if ($type === 'all' || $type === 'browsable') {
                        Utils::info("Generating ".$item['title']." html");
                        $mkd->setContent($content);
                        $scriptsPath = $this->buildDepth(count($arrayPath) - 1);
                        $content = "<html><head><title>" . htmlentities($item['title']) . "</title>" .
                            "<link rel=\"stylesheet\" href=\"" . $scriptsPath . "/style.css\" type=\"text/css\" />" .
                            "</head><body>" . $mkd->toHtml() .
                            "<script src=\"" . $scriptsPath . "/script.js\" />" .
                            "</body></html>";
                        file_put_contents(Utils::sanitizeFileName($htmlPath),
                            $content);
                    }
                }
            }

            // Create ZIP archive
            $zipPath = Utils::sanitizeFileName(sys_get_temp_dir() . '/export_.zip');
            $this->createZipArchive($tempDir, $zipPath);
        } catch (Exception $e) {

        } finally {
            $this->removeDirectory($tempDir);
        }

        // Clean up temporary directory


        return $zipPath;
    }

    /**
     * Process markdown content to replace API links with local paths
     *
     * @param string $content The markdown content
     * @return string The processed content
     */
    private function processMarkdownContent($content, $depth)
    {
        $depthString = $this->buildDepth($depth);
        //return $content;
        // Replace API links with local image paths
        //$pattern = '/\[([^\]]+)\]\([^)]*api\/files\.php\?action=get&id=([^.]+)\.([^)]+)\)/';
        $pattern = '#([/a-zA-Z_\\-]*)/api/files\.php\?action=get&id=([^.]+)\.([^)]+)\)#';
        $replacement = $depthString . 'img/$2.$3)';
        $result = preg_replace($pattern, $replacement, $content);
        return $result;
    }

    /**
     * Create a ZIP archive from directory
     *
     * @param string $sourceDir The source directory
     * @param string $outputPath The output ZIP file path
     * @return bool Success status
     */
    private function createZipArchive($sourceDir, $outputPath)
    {
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
    private function removeDirectory($dir)
    {
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
    private function sanitizeFilename($filename)
    {
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
    public function downloadZip($zipPath, $filename = 'export.zip')
    {
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

    /**
     * @param mixed $depth
     * @return string
     */
    public function buildDepth(mixed $depth): string
    {
        $depthArray = [];
        while ($depth > 0) {
            $depthArray[] = "../";
            $depth--;
        }
        $depthString = join("", $depthArray);
        return $depthString;
    }

    /**
     * Generate index.html with a tree menu for navigation
     *
     * @param string $tempDir Temporary directory where files are being exported
     * @param array $allFiles Array of all files to include in the menu
     */
    private function generateIndexHtml($tempDir, $allFiles)
    {
        // Build hierarchical structure
        $tree = [];
        $paths = [];
        foreach ($allFiles as $file) {
            $uuid = $file['id'];
            $path = $this->flatStorage->getFullPath($uuid);
            $paths[] = [
                'path' => $path,
                'id' => $uuid,
                'file' => $file];
        }
        uasort($paths, function ($a, $b) {
            return count($b['path']) - count($a['path']);
        });

        foreach ($paths as $idx => $pc) {
            $uuid = $pc['id'];
            $currentLevel = &$tree;
            $path = $pc['path'];

            // Build tree structure
            $filePath = [];
            foreach ($path as $index => $item) {

                $filePath[] = $item['id'];
                if ($index < count($path) - 1) {
                    // It's a folder
                    if (!isset($currentLevel[$item['id']])) {
                        $currentLevel[$item['id']] = [
                            'title' => $item['title'],
                            'type' => 'folder',
                            'path' => implode('/', $filePath) . '.html',
                            'children' => [],
                            'id' => $uuid
                        ];
                    }
                    $currentLevel = &$currentLevel[$item['id']]['children'];
                } else {
                    // It's a file
                    if (!isset($currentLevel[$item['id']])) {
                        $currentLevel[$item['id']] = [
                            'title' => $item['title'],
                            'type' => 'file',
                            'path' => implode('/', $filePath) . '.html',
                            'id' => $uuid
                        ];
                    }
                }
            }
        }

        // Generate HTML
        $html = $this->generateIndexHtmlContent($tree);
        file_put_contents($tempDir . '/index.html', $html);
    }

    /**
     * Generate HTML content for index.html
     *
     * @param array $tree Hierarchical tree structure of files
     * @return string HTML content
     */
    private function generateIndexHtmlContent($tree)
    {
        $html = <<<HTML
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Document Explorer</title>
    
	<head><link rel="stylesheet" href="style.css"></head>
</head>
<body>
    <div id="sidebar">
        <div class="tree-menu">
HTML;
        $partial = $this->renderTreeMenu($tree);
        $html .= $partial;

        $html .= <<<HTML
        </div>
    </div>
    <div id="content">
        <iframe id="content-frame" src="about:blank"></iframe>
    </div>
    
    <script src="script.js"></script>
</body>
</html>
HTML;

        return $html;
    }

    /**
     * Render tree menu HTML
     *
     * @param array $items Items to render
     * @return string HTML content
     */
    private function renderTreeMenu($items)
    {
        $html = '<ul>';

        foreach ($items as $key => $item) {
            if (array_key_exists('children', $item) && count($item['children']) > 0) {
                $html .= '<li>';
                $html .= '<div class="folder" data-path="' . htmlspecialchars($item['path']) . '">' . htmlspecialchars($item['title']) . '</div>';
                $html .= '<div class="folder-content">';
                $html .= $this->renderTreeMenu($item['children']);
                $html .= '</div>';
                $html .= '</li>';
            } else {
                $html .= '<li>';
                $html .= '<div class="file" data-path="' . htmlspecialchars($item['path']) . '">' .
                    htmlspecialchars($item['title']) . '</div>';
                $html .= '</li>';
            }
        }

        $html .= '</ul>';
        return $html;
    }
}