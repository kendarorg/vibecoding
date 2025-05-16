<?php

class Utils {
    /**
     * Generate a random UUID
     *
     * @return string The generated UUID
     */
    public static function generateUuid() {
        return sprintf(
            '%04x%04x-%04x-%04x-%04x-%04x%04x%04x',
            mt_rand(0, 0xffff), mt_rand(0, 0xffff),
            mt_rand(0, 0xffff),
            mt_rand(0, 0x0fff) | 0x4000,
            mt_rand(0, 0x3fff) | 0x8000,
            mt_rand(0, 0xffff), mt_rand(0, 0xffff), mt_rand(0, 0xffff)
        );
    }

    public static function errorLog($message) {

        error_log('[ERROR] '.date('Y-m-d H:i:s')." ".$message);
    }

    public static function sanitizeFileName($filename) {
        if(DIRECTORY_SEPARATOR=="/"){
            $filename = str_replace("\\", "/", $filename);
        }else{
            $filename = str_replace("/", "\\", $filename);
        }
        return $filename;
    }
}