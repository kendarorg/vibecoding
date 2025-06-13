package org.kendar.sync.util;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;

public class FilesUtils {
    public static String readFile(String filename) throws IOException
    {
        String content = null;
        File file = new File(filename); // For example, foo.txt
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            char[] chars = new char[(int) file.length()];
            reader.read(chars);
            content = new String(chars);
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(reader != null){
                reader.close();
            }
        }
        return content;
    }

    public static String readFile(Path lastCompactLogPath) {
        return readFile(lastCompactLogPath.toAbsolutePath());
    }
}
