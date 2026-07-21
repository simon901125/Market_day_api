package com.example.demo.Storage;

import java.io.IOException;
import java.io.InputStream;

public interface ImageFileStore {

    String save(String objectName, String contentType, InputStream inputStream) throws IOException;

    void delete(String objectName) throws IOException;
}
